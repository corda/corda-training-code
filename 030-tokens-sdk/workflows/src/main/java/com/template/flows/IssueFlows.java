package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.contracts.utilities.TransactionUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.states.AirMileType;
import javafx.util.Pair;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public interface IssueFlows {

    /**
     * Started by the {@link FungibleToken#getIssuer} to issue multiple states where it is the only issuer.
     * It is not an {@link InitiatingFlow} because it does not need to, it is {@link IssueTokens} that is initiating.
     */
    @StartableByRPC
    class Initiator extends FlowLogic<SignedTransaction> {

        /**
         * It may contain a given {@link Party} more than once, so that we can issue multiple states to a given holder.
         */
        @NotNull
        private final List<Pair<AbstractParty, Long>> heldQuantities;
        @NotNull
        private final ProgressTracker progressTracker;

        private final static Step PREPARING_TO_PASS_ON = new Step("Preparing to pass on to Tokens issue flow.");
        private final static Step PASSING_TO_SUB_ISSUE = new Step("Passing on to Tokens issue flow.");

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(PREPARING_TO_PASS_ON, PASSING_TO_SUB_ISSUE);
        }

        /**
         * This constructor would typically be called by RPC or by {@link FlowLogic#subFlow}.
         */
        public Initiator(@NotNull final List<Pair<AbstractParty, Long>> heldQuantities) {
            //noinspection ConstantConditions
            if (heldQuantities == null) throw new NullPointerException("heldQuantities cannot be null");
            this.heldQuantities = ImmutableList.copyOf(heldQuantities);
            this.progressTracker = tracker();
        }

        /**
         * The only constructor that can be called from the CLI.
         * Started by the issuer to issue a single state.
         */
        public Initiator(@NotNull final AbstractParty holder, final long quantity) {
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
            progressTracker.setCurrentStep(PREPARING_TO_PASS_ON);
            // It is a design decision to have this flow initiated by the issuer.
            final AirMileType airMileType = new AirMileType();
            final IssuedTokenType issuedAirMile = new IssuedTokenType(getOurIdentity(), airMileType);
            final SecureHash contractAttachment = TransactionUtilitiesKt.getAttachmentIdForGenericParam(airMileType);

            final List<FungibleToken> outputTokens = heldQuantities
                    // Thanks to the Stream, we are able to have our 'final List' in one go, instead of creating a
                    // modifiable one and then adding elements to it with for... add.
                    .stream()
                    // Change each element from a Pair to a FungibleToken.
                    .map(it -> new FungibleToken(
                            AmountUtilitiesKt.amount(it.getValue(), issuedAirMile),
                            it.getKey(),
                            contractAttachment))
                    // Get away from a Stream and back to a good ol' List.
                    .collect(Collectors.toList());

            progressTracker.setCurrentStep(PASSING_TO_SUB_ISSUE);
            final SignedTransaction notarised = subFlow(new IssueTokens(outputTokens, Collections.emptyList()));

            // We want our issuer to have a trace of the amounts that have been issued, whether it is a holder or not,
            // in order to know the total supply. Since the issuer is not in the participants, it needs to be done
            // manually. We do it after the sub flow as this is the better way to do, after notarisation, even if
            // here there is no notarisation.
            getServiceHub().recordTransactions(StatesToRecord.ALL_VISIBLE, Collections.singletonList(notarised));

            return notarised;
        }
    }
}
