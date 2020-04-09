package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.template.states.TokenState;
import javafx.util.Pair;
import net.corda.core.contracts.AttachmentResolutionException;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionResolutionException;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteriaUtils;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.security.SignatureException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.template.contracts.TokenContract.Commands.Redeem;

public interface RedeemFlows {

    /**
     * Started by a ${@link TokenState#getHolder()} to redeem multiple states where it is one of the holders.
     * Because it is an ${@link InitiatingFlow}, its counterpart flow ${@link Responder} is called automatically.
     * This constructor would be called by RPC or by ${@link FlowLogic#subFlow}. In particular one that would be more
     * user-friendly in terms of parameters passed. For instance, given sums, it would fetch the precise states in
     * the vault. Look at ${@link SimpleInitiator} for such an example.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {
        @NotNull
        private final List<StateAndRef<TokenState>> inputTokens;
        @NotNull
        private final ProgressTracker progressTracker;

        public final static Step GENERATING_TRANSACTION = new Step("Generating transaction based on parameters.");
        public final static Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        public final static Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        public final static Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        public final static Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION);
        }

        // By requiring an exact list of states, this flow assures absolute precision at the expense of
        // user-friendliness.
        public Initiator(@NotNull final List<StateAndRef<TokenState>> inputTokens,
                         @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (inputTokens == null) throw new NullPointerException("inputTokens cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("progressTracker cannot be null");
            this.inputTokens = ImmutableList.copyOf(inputTokens);
            this.progressTracker = progressTracker;
        }

        public Initiator(@NotNull final List<StateAndRef<TokenState>> inputTokens) {
            this(inputTokens, tracker());
        }

        @NotNull
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // We can only make a transaction if all states have to be marked by the same notary.
            final Set<Party> notaries = inputTokens.stream()
                    .map(it -> it.getState().getNotary())
                    .collect(Collectors.toSet());
            if (notaries.size() != 1) {
                throw new FlowException("There must be only 1 notary, not " + notaries.size());
            }
            final Party notary = notaries.iterator().next();

            final Set<Party> allSigners = inputTokens.stream()
                    .map(it -> it.getState().getData())
                    // Keep a mixed list of issuers and holders.
                    .flatMap(it -> Stream.of(it.getIssuer(), it.getHolder()))
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    .collect(Collectors.toSet());
            // We don't want to sign transactions where our signature is not needed.
            if (!allSigners.contains(getOurIdentity())) throw new FlowException("I must be an issuer or a holder.");

            // The issuers and holders are required signers, so we express this here.
            final Command<Redeem> txCommand = new Command<>(
                    new Redeem(),
                    allSigners.stream().map(Party::getOwningKey).collect(Collectors.toList()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addCommand(txCommand);
            inputTokens.forEach(txBuilder::addInputState);

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            txBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // We are but one of the signers.
            final SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(txBuilder);

            progressTracker.setCurrentStep(GATHERING_SIGS);
            // We need to gather the signatures of all issuers and all holders, except ourselves.
            final List<FlowSession> otherFlows = allSigners.stream()
                    // Remove myself.
                    .filter(it -> !it.equals(getOurIdentity()))
                    .map(this::initiateFlow)
                    .collect(Collectors.toList());
            final SignedTransaction fullySignedTx = otherFlows.isEmpty() ? partlySignedTx :
                    subFlow(new CollectSignaturesFlow(
                            partlySignedTx,
                            otherFlows,
                            GATHERING_SIGS.childProgressTracker()));

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(
                    fullySignedTx,
                    otherFlows,
                    FINALISING_TRANSACTION.childProgressTracker()));
        }
    }

    /**
     * When you create an initiator and an associated responder, you have no assurance that a peer will launch "your"
     * responder when you launch "your" initiator. Your peer may:
     * - use a totally different one that nonetheless follows the same back and forth choreography.
     * - use a sub-class in order to reuse the work you did on "your" responder.
     * Here we set up our responder to be able to be sub-classed by allowing others to introduce additional checks
     * on the transaction to be signed.
     * Additionally, when "your" responder is launched, you have no assurance that the peer that triggered the flow
     * used "your" initiator. The initiating peer may well have used a sub-class of "your" initiator.
     */
    abstract class Responder extends FlowLogic<SignedTransaction> {

        @NotNull
        private final FlowSession counterpartySession;
        @NotNull
        private final ProgressTracker progressTracker;

        public final static Step SIGNING_TRANSACTION = new Step("About to sign transaction with our private key.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return SignTransactionFlow.Companion.tracker();
            }
        };
        public final static Step FINALISING_TRANSACTION = new Step("Waiting to record transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION);
        }

        public Responder(@NotNull final FlowSession counterpartySession) {
            this(counterpartySession, tracker());
        }

        public Responder(@NotNull final FlowSession counterpartySession,
                         @NotNull final ProgressTracker progressTracker) {
            this.counterpartySession = counterpartySession;
            this.progressTracker = progressTracker;
        }

        @NotNull
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        /**
         * Peers can create sub-classes and extend the checks on the transaction by overriding this abstract function.
         * In particular, peers will be well advised to add logic here to control whether they really want this
         * transaction to happen.
         */
        protected abstract void additionalChecks(@NotNull final SignedTransaction stx) throws FlowException;

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignTransactionFlow signTransactionFlow = new SignTransactionFlow(counterpartySession) {
                @Override
                protected void checkTransaction(@NotNull final SignedTransaction stx) throws FlowException {
                    // We add our internal check for clients that want to extend this feature.
                    additionalChecks(stx);
                    // Here have the checks that presumably all peers will want to have. It is opinionated and peers
                    // are free to not use this class if they want to.
                    // Here we only automatically check that it is technically satisfactory.
                    // We don't like signing irrelevant transactions. I must be relevant.
                    final boolean relevant;
                    try {
                        relevant = stx.toLedgerTransaction(getServiceHub(), false)
                                .inputsOfType(TokenState.class)
                                .stream()
                                .anyMatch(it -> it.getIssuer().equals(getOurIdentity()) ||
                                        it.getHolder().equals(getOurIdentity()));
                    } catch (SignatureException | AttachmentResolutionException | TransactionResolutionException ex) {
                        throw new FlowException(ex);
                    }
                    if (!relevant) throw new FlowException("I must be relevant.");
                }
            };
            final SecureHash txId = subFlow(signTransactionFlow).getId();

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new ReceiveFinalityFlow(counterpartySession, txId));
        }
    }

    /**
     * This class associates a list of ${@link TokenState} and the sum of their ${@link TokenState#getQuantity}. This
     * helps us avoid constant recalculation.
     */
    class StateAccumulator {
        final private long maximumSum;
        final public long sum;
        @NotNull
        final public List<StateAndRef<TokenState>> states;

        public StateAccumulator(final long maximumSum) {
            this(maximumSum, Collections.emptyList());
        }

        public StateAccumulator(final long maximumSum, @NotNull final List<StateAndRef<TokenState>> states) {
            this(
                    maximumSum,
                    states.stream().reduce(0L,
                            (sum, state) -> Math.addExact(sum, state.getState().getData().getQuantity()),
                            Math::addExact),
                    states);
        }

        private StateAccumulator(
                final long maximumSum,
                final long sum,
                @NotNull final List<StateAndRef<TokenState>> states) {
            this.maximumSum = maximumSum;
            this.sum = sum;
            this.states = ImmutableList.copyOf(states);
        }

        /**
         * Joins 2 accumulators.
         */
        @NotNull
        public StateAccumulator plus(@NotNull final StateAccumulator other) {
            if (other.states.size() == 0) return this;
            final StateAndRef<TokenState> first = other.states.get(0);
            return this
                    .plus(first)
                    .plus(other.minus(first));
        }

        /**
         * Add a state only if the current sum is strictly below the max sum given,
         * and update the sum as we do.
         */
        @NotNull
        public StateAccumulator plus(@NotNull final StateAndRef<TokenState> state) {
            if (maximumSum <= sum) return this;
            final List<StateAndRef<TokenState>> joined = new ArrayList<>(states);
            joined.add(state);
            return new StateAccumulator(
                    maximumSum,
                    Math.addExact(sum, state.getState().getData().getQuantity()),
                    joined);
        }

        /**
         * Remove a state if it is found in the list. And update the sum as we do.
         */
        @NotNull
        public StateAccumulator minus(@NotNull final StateAndRef<TokenState> state) {
            if (!states.contains(state)) throw new IllegalArgumentException("State not found");
            return new StateAccumulator(
                    maximumSum,
                    Math.subtractExact(sum, state.getState().getData().getQuantity()),
                    states.stream().filter(it -> !it.equals(state)).collect(Collectors.toList())
            );
        }
    }


    /**
     * Allows to redeem a specific quantity of fungible tokens, as it assists in fetching them in the vault.
     */
    @StartableByRPC
    class SimpleInitiator extends FlowLogic<Pair<SignedTransaction, SignedTransaction>> {
        @NotNull
        private final Party issuer;
        private final long totalQuantity;
        /**
         * A basic search criteria for the vault.
         */
        @NotNull
        private final QueryCriteria tokenCriteria;
        @NotNull
        final private ProgressTracker progressTracker;

        private final static Step FETCHING_TOKEN_STATES = new ProgressTracker.Step("Fetching token states based on parameters.");
        private final static Step MOVING_TO_EXACT_COUNT = new ProgressTracker.Step("Moving token states so as to have an exact sum.");
        private final static Step HANDING_TO_INITIATOR = new ProgressTracker.Step("Handing to proper initiator.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return Initiator.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(
                    FETCHING_TOKEN_STATES,
                    MOVING_TO_EXACT_COUNT,
                    HANDING_TO_INITIATOR);
        }

        public SimpleInitiator(
                @NotNull final Party notary,
                @NotNull final Party issuer,
                @NotNull final Party holder,
                final long totalQuantity) {
            this(notary, issuer, holder, totalQuantity, tracker());
        }

        public SimpleInitiator(
                @NotNull final Party notary,
                @NotNull final Party issuer,
                @NotNull final Party holder,
                final long totalQuantity,
                @NotNull final ProgressTracker progressTracker) {
            this.issuer = issuer;
            if (totalQuantity <= 0) throw new IllegalArgumentException("totalQuantity must be positive");
            this.totalQuantity = totalQuantity;
            this.tokenCriteria = new QueryCriteria.VaultQueryCriteria()
                    .withParticipants(Collections.singletonList(holder))
                    .withNotary(Collections.singletonList(notary));
            this.progressTracker = progressTracker;
        }

        @NotNull
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @NotNull
        private StateAccumulator fetchWorthAtLeast(final long remainingSum) throws FlowException {
            return fetchWorthAtLeast(
                    remainingSum,
                    new PageSpecification(1, QueryCriteriaUtils.DEFAULT_PAGE_SIZE));
        }

        @NotNull
        private StateAccumulator fetchWorthAtLeast(
                final long remainingSum,
                @NotNull final PageSpecification paging) throws FlowException {
            // We reached the desired state already.
            if (remainingSum <= 0) return new StateAccumulator(0L);
            final List<StateAndRef<TokenState>> pagedStates = getServiceHub().getVaultService()
                    .queryBy(TokenState.class, tokenCriteria, paging)
                    .getStates();
            if (pagedStates.isEmpty()) throw new FlowException("Not enough states to reach sum.");

            final StateAccumulator fetched = pagedStates.stream()
                    // The previous query cannot pre-filter by issuer so we need to drop some here.
                    .filter(it -> it.getState().getData().getIssuer().equals(issuer))
                    // We will keep only up to the point where we have have enough.
                    .reduce(
                            new StateAccumulator(remainingSum),
                            StateAccumulator::plus,
                            StateAccumulator::plus);

            // Let's fetch some more, possibly an empty list.
            return fetched.plus(fetchWorthAtLeast(
                    // If this number is 0 or less, we will get an empty list.
                    remainingSum - fetched.sum,
                    // Take the next page
                    paging.copy(paging.getPageNumber() + 1, paging.getPageSize())
            ));
        }

        @Suspendable
        @Override
        public Pair<SignedTransaction, SignedTransaction> call() throws FlowException {
            progressTracker.setCurrentStep(FETCHING_TOKEN_STATES);
            final StateAccumulator accumulated = fetchWorthAtLeast(totalQuantity);

            progressTracker.setCurrentStep(MOVING_TO_EXACT_COUNT);
            // If we did not get an exact amount, we need to create some change for ourselves before we redeem the
            // exact quantity wanted.
            final SignedTransaction moveTx = accumulated.sum <= totalQuantity ? null :
                    subFlow(new MoveFlows.Initiator(accumulated.states, Arrays.asList(
                            new TokenState(issuer, getOurIdentity(), totalQuantity), // Index 0 in outputs.
                            new TokenState(issuer, getOurIdentity(), accumulated.sum - totalQuantity))));

            final List<StateAndRef<TokenState>> toUse = moveTx == null ? accumulated.states :
                    Collections.singletonList(moveTx.getTx().outRef(0));

            progressTracker.setCurrentStep(HANDING_TO_INITIATOR);
            return new Pair<>(moveTx, subFlow(new Initiator(
                    toUse,
                    HANDING_TO_INITIATOR.childProgressTracker())));
        }

    }

}
