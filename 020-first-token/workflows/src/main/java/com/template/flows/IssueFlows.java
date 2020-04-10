package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.template.contracts.TokenContract;
import com.template.contracts.TokenContract.Commands.Issue;
import com.template.states.TokenState;
import javafx.util.Pair;
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

    /**
     * Started by the {@link TokenState#getIssuer} to issue multiple states where it is the only issuer.
     * Because it is an {@link InitiatingFlow}, its counterpart flow {@link Responder} is called automatically.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        /**
         * It may contain a given {@link Party} more than once, so that we can issue multiple states to a given holder.
         */
        @NotNull
        private final List<Pair<Party, Long>> heldQuantities;
        @NotNull
        private final ProgressTracker progressTracker;

        private final static Step GENERATING_TRANSACTION = new Step("Generating transaction based on parameters.");
        private final static Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final static Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final static Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
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

        /**
         * This constructor would typically be called by RPC or by {@link FlowLogic#subFlow}.
         */
        public Initiator(@NotNull final List<Pair<Party, Long>> heldQuantities) {
            //noinspection ConstantConditions
            if (heldQuantities == null) throw new NullPointerException("heldQuantities cannot be null");
            if (heldQuantities.isEmpty()) throw new IllegalArgumentException("heldQuantities cannot be empty");
            final boolean noneZero = heldQuantities.stream().noneMatch(heldQuantity -> heldQuantity.getValue() <= 0);
            if (!noneZero) throw new IllegalArgumentException("heldQuantities must all be above 0");
            this.heldQuantities = ImmutableList.copyOf(heldQuantities);
            this.progressTracker = tracker();
        }

        /**
         * The only constructor that can be called from the CLI.
         * Started by the issuer to issue a single state.
         */
        public Initiator(@NotNull final Party holder, final long quantity) {
            this(Collections.singletonList(new Pair<>(holder, quantity)));
        }

        @NotNull
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // It is a design decision to have this flow initiated by the issuer.
            final Party issuer = getOurIdentity();
            final List<TokenState> outputTokens = heldQuantities
                    // Thanks to the Stream, we are able to have our 'final List' in one go, instead of creating a
                    // modifiable one and then adding elements to it with for... add.
                    .stream()
                    // Change each element from a Pair to a TokenState.
                    .map(it -> new TokenState(issuer, it.getKey(), it.getValue()))
                    // Get away from a Stream and back to a good ol' List.
                    .collect(Collectors.toList());
            // It is better practice to precisely define the accepted notary instead of picking the first one in the
            // list of notaries
            final Party notary = getServiceHub().getNetworkMapCache().getNotary(Constants.desiredNotary);
            if (notary == null) {
                throw new FlowException("The desired notary is not known: " + Constants.desiredNotary.toString());
            }

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // The issuer is a required signer, so we express this here
            final Command<Issue> txCommand = new Command<>(new Issue(), issuer.getOwningKey());
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addCommand(txCommand);
            outputTokens.forEach(it -> txBuilder.addOutputState(it, TokenContract.TOKEN_CONTRACT_ID));

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            txBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // We are the only issuer here, and the issuer's signature is required. So we sign.
            // There are no other signatures to collect.
            final SignedTransaction fullySignedTx = getServiceHub().signInitialTransaction(txBuilder);

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Thanks to the Stream, we are able to have our 'final List' in one go, instead of creating a modifiable
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

            final SignedTransaction notarised = subFlow(new FinalityFlow(
                    fullySignedTx,
                    holderFlows,
                    FINALISING_TRANSACTION.childProgressTracker()));

            // We want our issuer to have a trace of the amounts that have been issued, whether it is a holder or not,
            // in order to know the total supply. Since the issuer is not in the participants, it needs to be done
            // manually. We do it after the FinalityFlow as this is the better way to do, after notarisation, even if
            // here there is no notarisation.
            getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE, ImmutableList.of(notarised));

            return notarised;
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
