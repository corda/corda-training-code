package com.template.proposal.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.selection.TokenQueryBy;
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import com.template.proposal.state.SalesProposal;
import com.template.proposal.state.SalesProposalContract;
import kotlin.Pair;
import kotlin.jvm.functions.Function1;
import net.corda.core.contracts.*;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.r3.corda.lib.tokens.selection.database.config.DatabaseSelectionConfigKt.*;

public interface SalesProposalAcceptFlows {

    /**
     * Its responder flow is {@link AcceptSimpleHandlerFlow}.
     */
    @StartableByRPC
    @InitiatingFlow
    class AcceptSimpleFlow extends FlowLogic<SignedTransaction> {

        private final static ProgressTracker.Step FETCHING_PROPOSAL = new ProgressTracker.Step("Fetching proposal from the vault.");
        private final static ProgressTracker.Step PASSING_ON = new ProgressTracker.Step("Passing on to AcceptFlow.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return AcceptFlow.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(FETCHING_PROPOSAL, PASSING_ON);
        }

        @NotNull
        private final UniqueIdentifier proposalId;
        @NotNull
        private final ProgressTracker progressTracker;

        public AcceptSimpleFlow(
                @NotNull final UniqueIdentifier proposalId,
                @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (proposalId == null) throw new NullPointerException("The proposalId cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            this.proposalId = proposalId;
            this.progressTracker = progressTracker;
        }

        public AcceptSimpleFlow(
                @NotNull final UniqueIdentifier proposalId) {
            this(proposalId, tracker());
        }

        @Suspendable
        @NotNull
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(FETCHING_PROPOSAL);
            final QueryCriteria proposalCriteria = new QueryCriteria.LinearStateQueryCriteria()
                    .withUuid(Collections.singletonList(proposalId.getId()));
            final List<StateAndRef<SalesProposal>> proposals = getServiceHub().getVaultService()
                    .queryBy(SalesProposal.class, proposalCriteria)
                    .getStates();
            if (proposals.size() != 1) throw new FlowException("Wrong number of proposals found");
            final StateAndRef<SalesProposal> proposal = proposals.get(0);

            // We need to have been informed about this possibly anonymous identity ahead of time.
            progressTracker.setCurrentStep(PASSING_ON);
            return subFlow(new AcceptFlow(proposal, PASSING_ON.childProgressTracker()) {
                @NotNull
                @Override
                protected QueryCriteria getHeldByBuyer(
                        @NotNull final IssuedTokenType issuedCurrency,
                        @NotNull final AbstractParty buyer) {
                    return QueryUtilitiesKt.heldTokenAmountCriteria(issuedCurrency.getTokenType(), buyer);
                }
            });
        }
    }

    /**
     * Its handler is {@link AcceptHandlerFlow}.
     */
    abstract class AcceptFlow extends FlowLogic<SignedTransaction> {

        private final static Step GENERATING_TRANSACTION = new Step("Generating transaction based on parameters.");
        private final static Step MOVING_ASSET_TO_BUYER = new Step("Adding asset to buyer.");
        private final static Step PREPARING_TOKENS_FOR_PAYMENT = new Step("Preparing tokens for payment.");
        private final static Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final static Step RESOLVING_SELLER = new Step("Resolving host of seller.");
        private final static Step SENDING_STATE_REFS = new Step("Sending token state and refs.");
        private final static Step SENDING_MISSING_KEYS = new Step("Sending potentially missing keys.");
        private final static Step SIGNING_TRANSACTION = new Step("Signing transaction with our private keys.");
        public final static Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final static Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(
                    GENERATING_TRANSACTION,
                    MOVING_ASSET_TO_BUYER,
                    PREPARING_TOKENS_FOR_PAYMENT,
                    VERIFYING_TRANSACTION,
                    RESOLVING_SELLER,
                    SENDING_STATE_REFS,
                    SENDING_MISSING_KEYS,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION);
        }

        @NotNull
        private final StateAndRef<SalesProposal> proposalRef;
        @NotNull
        private final ProgressTracker progressTracker;

        public AcceptFlow(@NotNull final StateAndRef<SalesProposal> proposalRef,
                          @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (proposalRef == null) throw new NullPointerException("The proposalRef cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            this.proposalRef = proposalRef;
            this.progressTracker = progressTracker;
        }

        @SuppressWarnings("unused")
        public AcceptFlow(@NotNull final StateAndRef<SalesProposal> proposalRef) {
            this(proposalRef, tracker());
        }

        @NotNull
        abstract protected QueryCriteria getHeldByBuyer(
                @NotNull final IssuedTokenType issuedCurrency,
                @NotNull final AbstractParty buyer) throws FlowException;

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final SalesProposal proposal = proposalRef.getState().getData();
            final NonFungibleToken asset = proposal.getAsset().getState().getData();

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final TransactionBuilder builder = new TransactionBuilder(proposalRef.getState().getNotary())
                    // Accept the sales proposal
                    .addInputState(proposalRef)
                    .addCommand(new SalesProposalContract.Commands.Accept(),
                            Collections.singletonList(proposal.getBuyer().getOwningKey()))
                    .setTimeWindow(TimeWindow.untilOnly(proposal.getExpirationDate().minus(Duration.ofSeconds(1))));

            progressTracker.setCurrentStep(MOVING_ASSET_TO_BUYER);
            MoveTokensUtilitiesKt.addMoveNonFungibleTokens(builder, getServiceHub(),
                    asset.getToken().getTokenType(), proposal.getBuyer());

            progressTracker.setCurrentStep(PREPARING_TOKENS_FOR_PAYMENT);
            final IssuedTokenType issuedCurrency = proposal.getPrice().getToken();
            final QueryCriteria heldByBuyer = getHeldByBuyer(issuedCurrency, proposal.getBuyer());
            final Amount<TokenType> priceInCurrency = new Amount<>(
                    proposal.getPrice().getQuantity(),
                    proposal.getPrice().getToken());
            // Generate the buyer's currency inputs, to be spent, and the outputs, the currency tokens that will be
            // held by the seller.
            final DatabaseTokenSelection tokenSelection = new DatabaseTokenSelection(
                    getServiceHub(), MAX_RETRIES_DEFAULT, RETRY_SLEEP_DEFAULT, RETRY_CAP_DEFAULT, PAGE_SIZE_DEFAULT);
            final Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> moniesInOut = tokenSelection.generateMove(
                    // Eventually held by the seller.
                    Collections.singletonList(new Pair<>(proposal.getSeller(), priceInCurrency)),
                    // We see here that we should not rely on the default value, because the buyer keeps the change.
                    proposal.getBuyer(),
                    new TokenQueryBy(
                            issuedCurrency.getIssuer(),
                            (Function1<? super StateAndRef<? extends FungibleToken>, Boolean> & Serializable) it -> true,
                            heldByBuyer),
                    getRunId().getUuid());
            MoveTokensUtilitiesKt.addMoveTokens(builder, moniesInOut.getFirst(), moniesInOut.getSecond());

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            builder.verify(getServiceHub());

            progressTracker.setCurrentStep(RESOLVING_SELLER);
            final Party sellerHost = getServiceHub().getIdentityService()
                    .requireWellKnownPartyFromAnonymous(proposal.getSeller());
            final FlowSession sellerSession = initiateFlow(sellerHost);

            progressTracker.setCurrentStep(SENDING_STATE_REFS);
            // Send potentially missing StateRefs blindly.
            subFlow(new SendStateAndRefFlow(sellerSession, moniesInOut.getFirst()));

            progressTracker.setCurrentStep(SENDING_MISSING_KEYS);
            // Send potentially missing keys blindly.
            final List<AbstractParty> moniesKeys = moniesInOut.getFirst().stream()
                    .map(it -> it.getState().getData().getHolder())
                    .collect(Collectors.toList());
            subFlow(new SyncKeyMappingFlow(sellerSession, moniesKeys));

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final List<PublicKey> ourKeys = moniesKeys.stream()
                    .map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList());
            ourKeys.add(proposal.getBuyer().getOwningKey());
            final SignedTransaction acceptTx = getServiceHub().signInitialTransaction(builder, ourKeys);

            progressTracker.setCurrentStep(GATHERING_SIGS);
            final SignedTransaction signedTx = subFlow(new CollectSignaturesFlow(
                    acceptTx,
                    Collections.singletonList(sellerSession),
                    ourKeys,
                    GATHERING_SIGS.childProgressTracker()));

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(
                    signedTx,
                    Collections.singletonList(sellerSession),
                    FINALISING_TRANSACTION.childProgressTracker()));
        }
    }

    @InitiatedBy(AcceptSimpleFlow.class)
    class AcceptSimpleHandlerFlow extends AcceptHandlerFlow {

        public AcceptSimpleHandlerFlow(@NotNull FlowSession buyerSession) {
            super(buyerSession);
        }
    }

    /**
     * It is the handler of {@link AcceptFlow}.
     */
    class AcceptHandlerFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final FlowSession buyerSession;

        @SuppressWarnings("unused")
        public AcceptHandlerFlow(@NotNull final FlowSession buyerSession) {
            //noinspection ConstantConditions
            if (buyerSession == null) throw new NullPointerException("The buyerSession cannot be null");
            this.buyerSession = buyerSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Potentially missing StateRefs
            subFlow(new ReceiveStateAndRefFlow<>(buyerSession));
            // Receive potentially missing keys.
            subFlow(new SyncKeyMappingFlowHandler(buyerSession));

            // Sign as required.
            final SecureHash txId = subFlow(new SignTransactionFlow(buyerSession) {
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    // Let's make sure there is an Accept command.
                    final List<Command<?>> commands = stx.getTx().getCommands().stream()
                            .filter(it -> it.getValue() instanceof SalesProposalContract.Commands.Accept)
                            .collect(Collectors.toList());
                    if (commands.size() != 1)
                        throw new FlowException("There is no accept command");

                    final List<SalesProposal> proposals = new ArrayList<>(1);
                    final List<NonFungibleToken> assetsIn = new ArrayList<>(1);
                    final List<FungibleToken> moniesIn = new ArrayList<>(stx.getInputs().size());
                    for (final StateRef ref : stx.getInputs()) {
                        final ContractState state = getServiceHub().toStateAndRef(ref).getState().getData();
                        if (state instanceof SalesProposal)
                            proposals.add((SalesProposal) state);
                        else if (state instanceof NonFungibleToken)
                            assetsIn.add((NonFungibleToken) state);
                        else if (state instanceof FungibleToken)
                            moniesIn.add((FungibleToken) state);
                        else
                            throw new FlowException("Unexpected state class: " + state.getClass());
                    }
                    if (proposals.size() != 1) throw new FlowException("There should be a single sales proposal in");
                    if (assetsIn.size() != 1) throw new FlowException("There should be a single asset in");
                    final SalesProposal proposal = proposals.get(0);
                    final NonFungibleToken assetIn = assetsIn.get(0);
                    // If the asset does not match the proposal, it will be caught in the contract.

                    // Let's make sure we are signing with a single key.
                    final List<PublicKey> allInputKeys = moniesIn.stream()
                            .map(it -> it.getHolder().getOwningKey())
                            .collect(Collectors.toList());
                    allInputKeys.add(assetIn.getHolder().getOwningKey());
                    final List<PublicKey> myKeys = StreamSupport.stream(
                            getServiceHub().getKeyManagementService().filterMyKeys(allInputKeys).spliterator(),
                            false)
                            .collect(Collectors.toList());
                    if (myKeys.size() != 1) throw new FlowException("There are not the expected keys of mine");
                    if (!myKeys.get(0).equals(proposal.getSeller().getOwningKey()))
                        throw new FlowException("The key of mine is not the seller");

                    // Let's make sure the buyer is not trying to pass off some of our own monies as payment...
                    // After all, we are going to sign this transaction.
                    final List<FungibleToken> myInMonies = moniesIn.stream()
                            .filter(it -> it.getHolder().equals(proposal.getSeller()))
                            .collect(Collectors.toList());
                    if (!myInMonies.isEmpty())
                        throw new FlowException("There is a FungibleToken of mine in input");

                    // That I am paid is covered by the sales proposal contract.
                }
            }).getId();

            return subFlow(new ReceiveFinalityFlow(buyerSession, txId));
        }
    }

}
