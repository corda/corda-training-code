package com.template.proposal.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.template.proposal.state.SalesProposal;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public interface InformTokenBuyerFlows {

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
            buyerSession.send(buyer);
            subFlow(new SendTransactionFlow(buyerSession, tx));

            // In order to catch exceptions on the receiver side.
            buyerSession.receive(String.class).unwrap(it -> it);
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
            final AbstractParty buyer = sellerSession.receive(AbstractParty.class).unwrap(it -> it);
            if (!getServiceHub().getKeyManagementService()
                    .filterMyKeys(Collections.singletonList(buyer.getOwningKey())).iterator().hasNext())
                throw new FlowException("This buyer is not hosted here");

            final SignedTransaction tx = subFlow(new ReceiveTransactionFlow(sellerSession));
            final List<UniqueIdentifier> outputIds = tx.getCoreTransaction().outputsOfType(EvolvableTokenType.class)
                    .stream()
                    .map(EvolvableTokenType::getLinearId)
                    .collect(Collectors.toList());
            if (outputIds.isEmpty()) throw new FlowException("No EvolvableTokenType, stopping");

            // Do we have a SalesProposal with the seller?
            //noinspection unchecked
            final boolean relevant = getServiceHub().getVaultService().queryBy(
                    SalesProposal.class,
                    new QueryCriteria.LinearStateQueryCriteria().withParticipants(Collections.singletonList(buyer)))
                    .getStates()
                    .stream()
                    .map(proposal -> proposal.getState().getData()
                            .getAsset().getState().getData().getTokenType())
                    // You have to account for the fact that some SalesProposals may use a fixed TokenType.
                    .filter(TokenType::isPointer)
                    .map(it -> ((TokenPointer<EvolvableTokenType>) it).getPointer()
                            .resolve(getServiceHub())
                            .getState().getData().getLinearId())
                    .anyMatch(outputIds::contains);
            if (!relevant) throw new FlowException("There is no SalesProposal here for this transaction");

            // Finally satisfied that this transaction makes sense.
            getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE, Collections.singleton(tx));
            sellerSession.send("Ok");
            return null;
        }
    }

}
