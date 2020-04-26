package com.template.proposal.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.template.proposal.state.SalesProposal;
import com.template.proposal.state.SalesProposalContract;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.stream.Collectors;

public interface SalesProposalRejectFlows {

    class RejectFlow extends FlowLogic<SignedTransaction> {

        private final static ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on parameters.");
        private final static ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final static ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final static ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
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
                    FINALISING_TRANSACTION);
        }

        @NotNull
        private final StateAndRef<SalesProposal> proposal;
        @NotNull
        private final AbstractParty rejecter;
        @NotNull
        private final AbstractParty rejectee;
        @NotNull
        private final ProgressTracker progressTracker;

        public RejectFlow(@NotNull final StateAndRef<SalesProposal> proposal,
                          @NotNull final AbstractParty rejecter,
                          @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (proposal == null) throw new NullPointerException("The proposal cannot be null");
            //noinspection ConstantConditions
            if (rejecter == null) throw new NullPointerException("The rejecter cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            if (!proposal.getState().getData().getParticipants().contains(rejecter)) {
                throw new IllegalArgumentException("rejecter cannot reject the proposal");
            }
            this.proposal = proposal;
            this.rejecter = rejecter;
            this.rejectee = proposal.getState().getData().getParticipants().stream()
                    .filter(it -> !it.equals(rejecter))
                    .collect(Collectors.toList())
                    .get(0);
            this.progressTracker = progressTracker;
        }

        public RejectFlow(@NotNull final StateAndRef<SalesProposal> proposal,
                          @NotNull final AbstractParty rejecter) {
            this(proposal, rejecter, tracker());
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final TransactionBuilder builder = new TransactionBuilder(proposal.getState().getNotary())
                    .addInputState(proposal)
                    .addCommand(new SalesProposalContract.Commands.Reject(),
                            Collections.singletonList(rejecter.getOwningKey()));

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            builder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction rejectTx = getServiceHub().signInitialTransaction(
                    builder, rejecter.getOwningKey());

            // Resolve other host.
            final Party rejecteeHost = getServiceHub().getIdentityService()
                    .requireWellKnownPartyFromAnonymous(rejectee);

            // Inform the other.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(rejectTx, initiateFlow(rejecteeHost)));
        }
    }

    class RejectHandlerFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final FlowSession rejecterSession;

        @SuppressWarnings("unused")
        public RejectHandlerFlow(@NotNull final FlowSession rejecterSession) {
            //noinspection ConstantConditions
            if (rejecterSession == null) throw new NullPointerException("The rejecterSession cannot be null");
            this.rejecterSession = rejecterSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            return subFlow(new ReceiveFinalityFlow(rejecterSession));
        }
    }

}
