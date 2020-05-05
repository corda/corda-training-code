package com.template.proposal.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.template.proposal.state.SalesProposal;
import com.template.proposal.state.SalesProposalContract;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TimeWindow;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collections;
import java.util.stream.Collectors;

public interface SalesProposalRejectFlows {

    /**
     * Its handler is {@link RejectSimpleHandlerFlow};
     */
    @InitiatingFlow
    @StartableByRPC
    @SchedulableFlow
    class RejectSimpleFlow extends FlowLogic<SignedTransaction> {

        private final static ProgressTracker.Step FILTERING_MY_KEYS = new ProgressTracker.Step("Making sure the vault has the private key.");
        private final static ProgressTracker.Step FETCHING_PROPOSAL = new ProgressTracker.Step("Fetching proposal from the vault.");
        private final static ProgressTracker.Step PASSING_ON = new ProgressTracker.Step("Passing on to RejectFlow.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return RejectFlow.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(FILTERING_MY_KEYS, FETCHING_PROPOSAL, PASSING_ON);
        }

        @NotNull
        private final UniqueIdentifier proposalId;
        @NotNull
        private final AbstractParty rejecter;
        @NotNull
        private final ProgressTracker progressTracker;

        public RejectSimpleFlow(
                @NotNull final UniqueIdentifier proposalId,
                @NotNull final AbstractParty rejecter,
                @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (proposalId == null) throw new NullPointerException("The proposalId cannot be null");
            //noinspection ConstantConditions
            if (rejecter == null) throw new NullPointerException("The rejecter cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            this.proposalId = proposalId;
            this.rejecter = rejecter;
            this.progressTracker = progressTracker;
        }

        public RejectSimpleFlow(
                @NotNull final UniqueIdentifier proposalId,
                @NotNull final AbstractParty rejecter) {
            this(proposalId, rejecter, tracker());
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(FILTERING_MY_KEYS);
            if (!getServiceHub().getKeyManagementService().filterMyKeys(
                    Collections.singleton(rejecter.getOwningKey())).iterator().hasNext()) {
                throw new FlowException("Rejecter key unknown");
            }
            progressTracker.setCurrentStep(FETCHING_PROPOSAL);
            final StateAndRef<SalesProposal> proposal = new SalesProposalUtils(this)
                    .findBy(proposalId.getId());

            progressTracker.setCurrentStep(PASSING_ON);
            return subFlow(new RejectFlow(proposal, rejecter, PASSING_ON.childProgressTracker()));
        }
    }

    /**
     * Its handler is {@link RejectHandlerFlow}.
     */
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
                          // The rejecter is either the buyer or the seller.
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

        @SuppressWarnings("unused")
        public RejectFlow(@NotNull final StateAndRef<SalesProposal> proposal,
                          @NotNull final AbstractParty rejecter) {
            this(proposal, rejecter, tracker());
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final SalesProposal proposalState = proposal.getState().getData();
            final TransactionBuilder builder = new TransactionBuilder(proposal.getState().getNotary())
                    .addInputState(proposal)
                    .addCommand(new SalesProposalContract.Commands.Reject(),
                            Collections.singletonList(rejecter.getOwningKey()));
            if (proposalState.getSeller().equals(rejecter)) {
                builder.setTimeWindow(TimeWindow.fromOnly(
                        proposalState.getExpirationDate().plus(Duration.ofSeconds(1))));
            }

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            builder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            System.out.println("Xavier rejecting " + getOurIdentity().getName());
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

    @SuppressWarnings("unused")
    @InitiatedBy(RejectSimpleFlow.class)
    class RejectSimpleHandlerFlow extends RejectHandlerFlow {

        @SuppressWarnings("unused")
        public RejectSimpleHandlerFlow(@NotNull final FlowSession rejecterSession) {
            super(rejecterSession);
        }
    }

    /**
     * It is the handler of {@link RejectFlow}.
     */
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
