package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.move.AbstractMoveTokensFlow;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.r3.corda.lib.tokens.workflows.utilities.FlowUtilitiesKt.sessionsForParties;

public interface MoveFlows {

    /**
     * Started by a {@link FungibleToken#getHolder} to move multiple states where it is the only holder.
     * It is an {@link InitiatingFlow} flow and its counterpart, which already exists, is
     * {@link MoveFungibleTokensHandler}, while not being automatically {@link InitiatedBy} it.
     * This constructor would be called by RPC or by {@link FlowLogic#subFlow}. In particular one that, given sums,
     * fetches states in the vault.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        @NotNull
        private final List<StateAndRef<FungibleToken>> inputTokens;
        @NotNull
        private final List<FungibleToken> outputTokens;
        @NotNull
        private final ProgressTracker progressTracker;

        private final static Step PREPARING_TO_PASS_ON = new Step("Preparing to pass on to Tokens move flow.");
        private final static Step PASSING_TO_SUB_MOVE = new Step("Passing on to Tokens move flow.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return AbstractMoveTokensFlow.Companion.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(PREPARING_TO_PASS_ON, PASSING_TO_SUB_MOVE);
        }

        public Initiator(
                @NotNull final List<StateAndRef<FungibleToken>> inputTokens,
                @NotNull final List<FungibleToken> outputTokens,
                @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (inputTokens == null) throw new NullPointerException("inputTokens cannot be null");
            final Set<AbstractParty> holders = inputTokens.stream()
                    .map(it -> it.getState().getData().getHolder())
                    .collect(Collectors.toSet());
            if (holders.size() != 1) throw new IllegalArgumentException("There can be only one holder");
            this.inputTokens = inputTokens;
            //noinspection ConstantConditions
            if (outputTokens == null) throw new NullPointerException("outputTokens cannot be null");
            this.outputTokens = outputTokens;
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("progressTracker cannot be null");
            this.progressTracker = progressTracker;
        }

        public Initiator(@NotNull final List<StateAndRef<FungibleToken>> inputTokens,
                         @NotNull final List<FungibleToken> outputTokens) {
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
            progressTracker.setCurrentStep(PREPARING_TO_PASS_ON);
            final Set<AbstractParty> allHolders = inputTokens.stream()
                    // Only the input holder is necessary on a Move.
                    .map(it -> it.getState().getData().getHolder())
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    .collect(Collectors.toSet());
            // We don't want to sign transactions where our signature is not needed.
            if (!allHolders.contains(getOurIdentity())) throw new FlowException("I must be a holder.");

            final Set<AbstractParty> participantsIn = inputTokens.stream()
                    .map(it -> it.getState().getData().getHolder())
                    .collect(Collectors.toSet());
            final Set<AbstractParty> participantsOut = outputTokens.stream()
                    .map(FungibleToken::getHolder)
                    .collect(Collectors.toSet());
            final Set<AbstractParty> allParticipants = new HashSet<>(participantsIn);
            allParticipants.addAll(participantsOut);
            final List<FlowSession> participantSessions = sessionsForParties(this, new ArrayList<>(allParticipants));

            progressTracker.setCurrentStep(PASSING_TO_SUB_MOVE);
            return subFlow(new AbstractMoveTokensFlow() {
                @NotNull
                @Override
                public List<FlowSession> getParticipantSessions() {
                    return participantSessions;
                }

                @NotNull
                @Override
                public List<FlowSession> getObserverSessions() {
                    return Collections.emptyList();
                }

                @NotNull
                @Override
                public ProgressTracker getProgressTracker() {
                    return PASSING_TO_SUB_MOVE.childProgressTracker();
                }

                @Override
                public void addMove(@NotNull TransactionBuilder transactionBuilder) {
                    MoveTokensUtilitiesKt.addMoveTokens(transactionBuilder, inputTokens, outputTokens);
                }
            });
        }
    }

}