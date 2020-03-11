package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.template.contracts.TokenContract;
import com.template.contracts.TokenContract.Commands.Issue;
import com.template.states.TokenState;
import net.corda.core.contracts.Command;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public interface IssueFlows {

    class Pair<T, U> {
        public final T first;
        public final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Started by the {@link TokenState#getIssuer} to issue multiple states where it is the only issuer.
     * Because it is an {@link InitiatingFlow}, its counterpart flow {@link Responder} is called automatically.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        @SuppressWarnings("DanglingJavadoc")
        @NotNull
        /**
         * It may contain a given {@link Party} more than once, so that we can issue multiple states to a given holder.
         */
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

        /**
         * This constructor would typically be called by RPC or by {@link FlowLogic#subFlow}.
         */
        public Initiator(@NotNull final List<Pair<Party, Long>> heldQuantities) {
            this.heldQuantities = heldQuantities;
        }

        /**
         * The only constructor that can be called from the CLI.
         * Started by the issuer to issue a single state.
         */
        public Initiator(@NotNull final Party holder, final long quantity) {
            this(Collections.singletonList(new Pair<>(holder, quantity)));
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
                    // Thanks to the Stream, we are able to have our List final in one go, instead of creating a
                    // modifiable one and then adding elements to it with for... add.
                    .stream()
                    // Change each element from a Pair to a TokenState.
                    .map(it -> new TokenState(issuer, it.first, it.second))
                    // Get away from a Stream and back to a good ol' List.
                    .collect(Collectors.toList());
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final Command<Issue> txCommand = new Command<>(new Issue(), issuer.getOwningKey());
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addCommand(txCommand);
            outputTokens.forEach(it -> txBuilder.addOutputState(it, TokenContract.TOKEN_CONTRACT_ID));

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // We are the only issuer here, and the issuer's signature is required. So we sign.
            // There are no other signatures to collect.
            final SignedTransaction fullySignedTx = getServiceHub().signInitialTransaction(txBuilder);

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            txBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Thanks to the Stream, we are able to have our List final in one go, instead of creating a modifiable
            // one and then conditionally adding elements to it with for... add.
            final List<FlowSession> holderFlows = outputTokens.stream()
                    // Extract the holder Party from the token
                    .map(TokenState::getHolder)
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    // If we did not use a Stream we could for instance use a Set.
                    .distinct()
                    // Remove myself from the Stream.
                    // I already know what I am doing so no need to inform myself with a separate flow.
                    .filter(it -> !it.equals(issuer))
                    // Change each element of Stream from a Party to a FlowSession.
                    .map(this::initiateFlow)
                    // Get away from a Stream and back to a good ol' List.
                    .collect(Collectors.toList());

            // We want our issuer to have a trace of the amounts that have been issued, whether it is a holder or not,
            // in order to know the total supply. Since the issuer is not in the participants, it needs to be done
            // manually.
            getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE, ImmutableList.of(fullySignedTx));

            return subFlow(new FinalityFlow(
                    fullySignedTx,
                    holderFlows,
                    FINALISING_TRANSACTION.childProgressTracker()));
        }
    }


    @InitiatedBy(Initiator.class)
    class Responder extends FlowLogic<SignedTransaction> {

        @NotNull
        private final FlowSession counterpartySession;

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
