package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.template.contracts.TokenContract;
import com.template.contracts.TokenContract.Commands.Issue;
import com.template.states.TokenState;
import net.corda.core.contracts.Command;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public interface IssueFlow {

    class Pair<T, U> {
        public final T first;
        public final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        @NotNull
        private final List<Pair<Party, Long>> heldQuantities;

        private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new IOU.");
        private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
        );

        public Initiator(@NotNull final List<Pair<Party, Long>> heldQuantities) {
            this.heldQuantities = heldQuantities;
        }

        public Initiator(@NotNull final Pair<Party, Long> heldQuantity) {
            this(Collections.singletonList(heldQuantity));
        }

        public Initiator(@NotNull final Party holder, final long quantity) {
            this(new Pair<>(holder, quantity));
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            final Party issuer = getOurIdentity();
            final List<TokenState> outputTokens = heldQuantities
                    .stream()
                    .map(it -> new TokenState(issuer, it.first, it.second))
                    .collect(Collectors.toList());
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final Command<Issue> txCommand = new Command<>(new Issue(), issuer.getOwningKey());
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addCommand(txCommand);
            outputTokens.forEach(it -> txBuilder.addOutputState(it, TokenContract.TOKEN_CONTRACT_ID));

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction fullySignedTx = getServiceHub().signInitialTransaction(txBuilder);

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            txBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            final List<FlowSession> holderFlows = outputTokens
                    .stream()
                    .map(TokenState::getHolder)
                    // Duplicates would be an issue when initiating flows, at least.
                    .distinct()
                    // I do not need to inform myself separately.
                    .filter(it -> !it.equals(issuer))
                    .map(this::initiateFlow)
                    .collect(Collectors.toList());

            return subFlow(new FinalityFlow(
                    fullySignedTx,
                    holderFlows,
                    FINALISING_TRANSACTION.childProgressTracker()));
        }
    }


    @InitiatedBy(Initiator.class)
    class Responder extends FlowLogic<SignedTransaction> {

        @NotNull private final FlowSession counterpartySession;

        public Responder(@NotNull final FlowSession counterpartySession) {
            this.counterpartySession = counterpartySession;
        }


        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            return subFlow(new ReceiveFinalityFlow(counterpartySession));
        }
    }

}
