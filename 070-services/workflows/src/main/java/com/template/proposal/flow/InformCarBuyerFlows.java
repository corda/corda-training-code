package com.template.proposal.flow;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public interface InformCarBuyerFlows {

    /**
     * Its handler is {@link Receive}.
     */
    @InitiatingFlow
    @StartableByService
    @StartableByRPC
    class Send extends FlowLogic<Void> {

        private final static ProgressTracker.Step FETCHING_HOST = new ProgressTracker.Step("Fetching host of the buyer.");
        private final static ProgressTracker.Step SENDING = new ProgressTracker.Step("Sending information.");

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(FETCHING_HOST, SENDING);
        }

        @NotNull
        private final AbstractParty buyer;
        @NotNull
        private final SignedTransaction tx;
        @NotNull
        private final ProgressTracker progressTracker;

        public Send(@NotNull final AbstractParty buyer,
                    @NotNull final SignedTransaction tx,
                    @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (buyer == null) throw new NullPointerException("The buyer cannot be null");
            //noinspection ConstantConditions
            if (tx == null) throw new NullPointerException("The tx cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            this.buyer = buyer;
            this.tx = tx;
            this.progressTracker = progressTracker;
        }

        public Send(@NotNull final AbstractParty buyer,
                    @NotNull final SignedTransaction tx) {
            this(buyer, tx, tracker());
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            progressTracker.setCurrentStep(FETCHING_HOST);
            final Party buyerHost = getServiceHub().getIdentityService()
                    .requireWellKnownPartyFromAnonymous(buyer);
            final FlowSession buyerSession = initiateFlow(buyerHost);

            progressTracker.setCurrentStep(SENDING);
            subFlow(new SendTransactionFlow(buyerSession, tx));

            return null;
        }
    }

    @InitiatedBy(Send.class)
    class Receive extends FlowLogic<Void> {

        @NotNull
        private final FlowSession sellerSession;

        public Receive(@NotNull final FlowSession sellerSession) {
            //noinspection ConstantConditions
            if (sellerSession == null) throw new NullPointerException("The sellerSession cannot be null");
            this.sellerSession = sellerSession;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            final SignedTransaction tx = subFlow(new ReceiveTransactionFlow(sellerSession));
            getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE, Collections.singleton(tx));
            return null;
        }
    }

}
