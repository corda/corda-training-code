package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlow;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface RedeemFlows {

    /**
     * Started by a ${@link FungibleToken#getHolder()} to redeem multiple states of 1 issuer where it is one of the holders.
     * It is not ${@link InitiatingFlow}.
     * This constructor would be called by RPC or by ${@link FlowLogic#subFlow}.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {
        @NotNull
        private final List<StateAndRef<FungibleToken>> inputTokens;
        @NotNull
        private final ProgressTracker progressTracker;

        private final static Step PREPARING_TO_PASS_ON = new Step("Preparing to pass on to Tokens redeem flow.");
        private final static Step PASSING_TO_SUB_REDEEM = new Step("Passing on to Tokens redeem flow.");

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(PREPARING_TO_PASS_ON, PASSING_TO_SUB_REDEEM);
        }

        // By requiring an exact list of states, this flow assures absolute precision at the expense of
        // user-friendliness.
        public Initiator(@NotNull final List<StateAndRef<FungibleToken>> inputTokens,
                         @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (inputTokens == null) throw new NullPointerException("inputTokens cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("progressTracker cannot be null");
            this.inputTokens = ImmutableList.copyOf(inputTokens);
            this.progressTracker = progressTracker;
        }

        public Initiator(@NotNull final List<StateAndRef<FungibleToken>> inputTokens) {
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
            progressTracker.setCurrentStep(PREPARING_TO_PASS_ON);
            final Set<Party> allIssuers = inputTokens.stream()
                    .map(it -> it.getState().getData().getIssuer())
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    .collect(Collectors.toSet());
            // We don't want to sign transactions where our signature is not needed.
            if (allIssuers.size() != 1) throw new FlowException("It can only redeem one issuer at a time.");
            final FlowSession issuerSession = initiateFlow(allIssuers.iterator().next());

            progressTracker.setCurrentStep(PASSING_TO_SUB_REDEEM);
            return subFlow(new RedeemTokensFlow(
                    inputTokens,
                    null,
                    issuerSession));
        }
    }

}
