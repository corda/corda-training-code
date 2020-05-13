package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.IOUContract;
import com.example.state.IOUState;
import com.example.state.KYCState;
import com.example.state.KYCState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IOU encapsulated
 * within an [IOUState].
 * <p>
 * In our simple example, the [Acceptor] always accepts a valid IOU.
 * <p>
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 * <p>
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
public class ExampleFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final int iouValue;
        @NotNull
        private final Party otherParty;
        @NotNull
        private final Party kycIssuer;

        private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new IOU.");
        private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Initiator(final int iouValue,
                         @NotNull final Party otherParty,
                         @NotNull final Party kycIssuer) {
            this.iouValue = iouValue;
            this.otherParty = otherParty;
            this.kycIssuer = kycIssuer;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            Party me = getOurIdentity();
            IOUState iouState = new IOUState(iouValue, me, otherParty, kycIssuer, new UniqueIdentifier());
            // Inside the com.example.flow.ExampleFlow which creates an IOU:

            // Query the vault to get the lender and borrower related KYC StateAndRef with the appropriate lender.
            final QueryCriteria participantsKyc = new QueryCriteria.VaultQueryCriteria()
                    .withParticipants(Arrays.asList(
                            iouState.getBorrower(),
                            iouState.getLender(),
                            iouState.getKycIssuer()));
            final List<StateAndRef<KYCState>> kycRefs = getServiceHub().getVaultService()
                    .queryBy(KYCState.class, participantsKyc).getStates().stream()
                    // Keep relevant ones
                    .filter(it -> it.getState().getData().getIssuer().equals(iouState.getKycIssuer()))
                    .collect(Collectors.toList());
            // TODO less harsh test.
            if (kycRefs.size() != 2)
                throw new FlowException("KYC data for IOU participants not found.");

            final Command<IOUContract.Commands.Create> txCommand = new Command<>(
                    new IOUContract.Commands.Create(),
                    ImmutableList.of(iouState.getLender().getOwningKey(), iouState.getBorrower().getOwningKey()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(iouState, IOUContract.ID)
                    .addCommand(txCommand)
                    // Add KYC data as references to the transaction.
                    .addReferenceState(kycRefs.get(0).referenced())
                    .addReferenceState(kycRefs.get(1).referenced());

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            FlowSession otherPartySession = initiateFlow(otherParty);
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));

            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx, ImmutableSet.of(otherPartySession)));
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        @NotNull
        private final FlowSession otherPartySession;

        public Acceptor(@NotNull final  FlowSession otherPartySession) {
            this.otherPartySession = otherPartySession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(@NotNull final SignedTransaction stx) {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be an IOU transaction.", output instanceof IOUState);
                        //noinspection ConstantConditions
                        IOUState iou = (IOUState) output;
                        require.using("I won't accept IOUs with a value over 100.", iou.getValue() <= 100);
                        return null;
                    });
                }
            }
            final SignTxFlow signTxFlow = new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker());
            final SecureHash txId = subFlow(signTxFlow).getId();

            return subFlow(new ReceiveFinalityFlow(otherPartySession, txId));
        }
    }
}
