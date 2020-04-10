package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.contracts.TokenContract;
import com.template.contracts.TokenContract.Commands.Move;
import com.template.states.TokenState;
import net.corda.core.contracts.AttachmentResolutionException;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionResolutionException;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface MoveFlows {

    /**
     * The different transaction roles expected of the responder.
     * A signer needs to sign, a participant only needs to receive the result.
     */
    @CordaSerializable
    enum TransactionRole {SIGNER, PARTICIPANT}

    /**
     * Started by a {@link TokenState#getHolder} to move multiple states where it is one of the holders.
     * Because it is an {@link InitiatingFlow}, its counterpart flow {@link Responder} is called automatically.
     * This constructor would be called by RPC or by {@link FlowLogic#subFlow}. In particular one that, given sums,
     * fetches states in the vault.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        @NotNull
        private final List<StateAndRef<TokenState>> inputTokens;
        @NotNull
        private final List<TokenState> outputTokens;
        @NotNull
        private final ProgressTracker progressTracker;

        private final static Step GENERATING_TRANSACTION = new Step("Generating transaction based on parameters.");
        private final static Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final static Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final static Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.tracker();
            }
        };

        private final static Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.tracker();
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

        public Initiator(
                @NotNull final List<StateAndRef<TokenState>> inputTokens,
                @NotNull final List<TokenState> outputTokens,
                @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (inputTokens == null) throw new NullPointerException("inputTokens cannot be null");
            if (inputTokens.isEmpty()) throw new IllegalArgumentException("inputTokens cannot be empty");
            //noinspection ConstantConditions
            if (outputTokens == null) throw new NullPointerException("outputTokens cannot be null");
            if (outputTokens.isEmpty()) throw new IllegalArgumentException("outputTokens cannot be empty");
            final boolean noneZero = outputTokens.stream().noneMatch(outputToken -> outputToken.getQuantity() <= 0);
            if (!noneZero) throw new IllegalArgumentException("outputTokens quantities must all be above 0");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("progressTracker cannot be null");
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.progressTracker = progressTracker;
        }

        public Initiator(@NotNull final List<StateAndRef<TokenState>> inputTokens,
                         @NotNull final List<TokenState> outputTokens) {
            this(inputTokens, outputTokens, tracker());
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
                    // This gets rid of duplicates.
                    .collect(Collectors.toSet());
            if (notaries.size() != 1) {
                throw new FlowException("There must be only 1 notary, not " + notaries.size());
            }
            final Party notary = notaries.iterator().next();

            final Set<Party> allSigners = inputTokens.stream()
                    // Only the input holder is necessary on a Move.
                    .map(it -> it.getState().getData().getHolder())
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    .collect(Collectors.toSet());
            // We don't want to sign transactions where our signature is not needed.
            if (!allSigners.contains(getOurIdentity())) throw new FlowException("I must be a holder.");


            // The issuers and holders are required signers, so we express this here.
            final Command<Move> txCommand = new Command<>(
                    new Move(),
                    allSigners.stream().map(Party::getOwningKey).collect(Collectors.toList()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addCommand(txCommand);
            inputTokens.forEach(txBuilder::addInputState);
            outputTokens.forEach(it -> txBuilder.addOutputState(it, TokenContract.TOKEN_CONTRACT_ID));

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            txBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // We are but one of the signers.
            final SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(txBuilder);

            progressTracker.setCurrentStep(GATHERING_SIGS);
            // We need to gather the signatures of all issuers and all holders, except ourselves.
            final List<FlowSession> signerFlows = allSigners.stream()
                    // We don't need to inform ourselves and we signed already.
                    .filter(it -> !it.equals(getOurIdentity()))
                    .map(this::initiateFlow)
                    .collect(Collectors.toList());
            // Prime these responders to act in a signer type of way.
            // We need to use `for` instead of `.forEach` because we would need to annotate the lambda with
            // @Suspendable.
            for (final FlowSession it : signerFlows) {
                it.send(TransactionRole.SIGNER);
            }
            final SignedTransaction fullySignedTx = signerFlows.isEmpty() ? partlySignedTx :
                    subFlow(new CollectSignaturesFlow(
                            partlySignedTx,
                            signerFlows,
                            GATHERING_SIGS.childProgressTracker()));

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // The new holders that are not signers and still need to be informed.
            final List<FlowSession> newHolderFlows = outputTokens.stream()
                    .map(TokenState::getHolder)
                    .distinct()
                    // The signers are being handled in the other flows.
                    .filter(it -> !allSigners.contains(it))
                    .map(this::initiateFlow)
                    .collect(Collectors.toList());
            // Prime these responders to act in a holder type of way.
            // We need to use `for` instead of `.forEach` because we would need to annotate the lambda with
            // @Suspendable.
            for (final FlowSession it : newHolderFlows) {
                it.send(TransactionRole.PARTICIPANT);
            }
            final List<FlowSession> allFlows = new ArrayList<>(signerFlows);
            allFlows.addAll(newHolderFlows);
            return subFlow(new FinalityFlow(
                    fullySignedTx,
                    // All of them need to finalise.
                    allFlows,
                    FINALISING_TRANSACTION.childProgressTracker()));
        }
    }

    class Responder extends FlowLogic<SignedTransaction> {
        @NotNull
        private final FlowSession counterpartySession;
        @NotNull
        private final ProgressTracker progressTracker;

        private final static Step RECEIVING_ROLE = new Step("Receiving role to impersonate.");
        private final static Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return SignTransactionFlow.Companion.tracker();
            }
        };

        private final static Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(
                    RECEIVING_ROLE,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION);
        }

        public Responder(@NotNull final FlowSession counterpartySession) {
            this.counterpartySession = counterpartySession;
            this.progressTracker = tracker();
        }

        @NotNull
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(RECEIVING_ROLE);
            final TransactionRole myRole = counterpartySession.receive(TransactionRole.class).unwrap(it -> it);

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SecureHash txId;
            switch (myRole) {
                // We do not need to sign.
                case PARTICIPANT:
                    txId = null;
                    break;
                case SIGNER: {
                    final SignTransactionFlow signTransactionFlow = new SignTransactionFlow(counterpartySession) {
                        @Override
                        protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                            // Notice that there is still a security risk here as my node can be asked to sign
                            // without my human knowledge.
                            // I must be relevant. We don't like signing irrelevant transactions.
                            final boolean relevant;
                            try {
                                relevant = stx.toLedgerTransaction(getServiceHub(), false)
                                        .inputsOfType(TokenState.class)
                                        .stream()
                                        .anyMatch(it -> it.getHolder().equals(getOurIdentity()));
                            } catch (SignatureException | AttachmentResolutionException | TransactionResolutionException ex) {
                                throw new FlowException(ex);
                            }
                            if (!relevant) throw new FlowException("I must be relevant.");
                        }
                    };
                    txId = subFlow(signTransactionFlow).getId();
                }
                break;
                default:
                    throw new FlowException("Unexpected value: " + myRole);
            }

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new ReceiveFinalityFlow(counterpartySession, txId));
        }
    }

}