package com.template.proposal.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.template.proposal.state.SalesProposal;
import com.template.proposal.state.SalesProposalContract;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ReferencedStateAndRef;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.r3.corda.lib.tokens.workflows.utilities.NotaryUtilitiesKt.firstNotary;
import static com.r3.corda.lib.tokens.workflows.utilities.NotaryUtilitiesKt.getPreferredNotary;

public interface SalesProposalOfferFlows {

    /**
     * Its handler is {@link OfferSimpleHandlerFlow}.
     */
    @StartableByRPC
    @InitiatingFlow
    class OfferSimpleFlow extends FlowLogic<SignedTransaction> {

        private final static Step FETCHING_ASSET = new Step("Fetching asset from the vault.");
        private final static Step PASSING_ON = new Step("Passing on to OfferFlow.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return OfferFlow.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(FETCHING_ASSET, PASSING_ON);
        }

        @NotNull
        private final UniqueIdentifier assetId;
        @NotNull
        private final Party buyer;
        private final long price;
        @NotNull
        private final TokenType currency;
        @NotNull
        private final Party issuer;
        @NotNull
        private final ProgressTracker progressTracker;

        public OfferSimpleFlow(@NotNull final UniqueIdentifier assetId,
                               @NotNull final Party buyer,
                               final long price,
                               @NotNull final String currencyCode,
                               @NotNull final Party issuer,
                               @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (assetId == null) throw new NullPointerException("The assetId cannot be null");
            //noinspection ConstantConditions
            if (buyer == null) throw new NullPointerException("The buyer cannot be null");
            //noinspection ConstantConditions
            if (currencyCode == null) throw new NullPointerException("The currency cannot be null");
            //noinspection ConstantConditions
            if (issuer == null) throw new NullPointerException("The issuer cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            this.assetId = assetId;
            this.buyer = buyer;
            this.price = price;
            this.currency = FiatCurrency.Companion.getInstance(currencyCode);
            this.issuer = issuer;
            this.progressTracker = progressTracker;
        }

        @SuppressWarnings("unused")
        public OfferSimpleFlow(@NotNull final UniqueIdentifier assetId,
                               @NotNull final Party buyer,
                               final long price,
                               @NotNull final String currencyCode,
                               @NotNull final Party issuer) {
            this(assetId, buyer, price, currencyCode, issuer, tracker());
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(FETCHING_ASSET);
            final QueryCriteria assetCriteria = new QueryCriteria.LinearStateQueryCriteria()
                    .withUuid(Collections.singletonList(assetId.getId()));
            final List<StateAndRef<NonFungibleToken>> assets = getServiceHub().getVaultService()
                    .queryBy(NonFungibleToken.class, assetCriteria)
                    .getStates();
            if (assets.size() != 1) throw new FlowException("Wrong number of assets found");
            final StateAndRef<NonFungibleToken> asset = assets.get(0);

            progressTracker.setCurrentStep(PASSING_ON);
            return subFlow(new OfferFlow(asset, buyer,
                    AmountUtilitiesKt.amount(price, new IssuedTokenType(issuer, currency)),
                    PASSING_ON.childProgressTracker()));
        }
    }

    /**
     * Its handler is {@link OfferHandlerFlow}.
     */
    class OfferFlow extends FlowLogic<SignedTransaction> {

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

        @NotNull
        private final StateAndRef<NonFungibleToken> asset;
        @NotNull
        private final AbstractParty buyer;
        @NotNull
        private final Amount<IssuedTokenType> price;
        @NotNull
        private final ProgressTracker progressTracker;

        public OfferFlow(@NotNull final StateAndRef<NonFungibleToken> asset,
                         @NotNull final AbstractParty buyer,
                         @NotNull final Amount<IssuedTokenType> price,
                         @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (asset == null) throw new NullPointerException("The asset cannot be null");
            //noinspection ConstantConditions
            if (buyer == null) throw new NullPointerException("The buyer cannot be null");
            //noinspection ConstantConditions
            if (price == null) throw new NullPointerException("The price cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            this.asset = asset;
            this.buyer = buyer;
            this.price = price;
            this.progressTracker = progressTracker;
        }

        @SuppressWarnings("unused")
        public OfferFlow(@NotNull final StateAndRef<NonFungibleToken> asset,
                         @NotNull final AbstractParty buyer,
                         @NotNull final Amount<IssuedTokenType> price) {
            this(asset, buyer, price, tracker());
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final SalesProposal proposal = new SalesProposal(new UniqueIdentifier(), asset, buyer, price);
            final TransactionBuilder builder = new TransactionBuilder(asset.getState().getNotary())
                    .addOutputState(proposal)
                    .addReferenceState(new ReferencedStateAndRef<>(asset))
                    .addCommand(new SalesProposalContract.Commands.Offer(),
                            Collections.singletonList(proposal.getSeller().getOwningKey()));

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            builder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction offerTx = getServiceHub().signInitialTransaction(
                    builder, proposal.getSeller().getOwningKey());

            // Resolve buyer host.
            final Party buyerHost = getServiceHub().getIdentityService()
                    .requireWellKnownPartyFromAnonymous(proposal.getBuyer());

            // Inform buyer.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(
                    offerTx,
                    Collections.singletonList(initiateFlow(buyerHost)),
                    FINALISING_TRANSACTION.childProgressTracker()));
        }
    }

    @SuppressWarnings("unused")
    @InitiatedBy(OfferSimpleFlow.class)
    class OfferSimpleHandlerFlow extends OfferHandlerFlow {

        public OfferSimpleHandlerFlow(@NotNull final FlowSession sellerSession) {
            super(sellerSession);
        }
    }

    /**
     * It is the handler of {@link OfferFlow}.
     */
    class OfferHandlerFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final FlowSession sellerSession;

        public OfferHandlerFlow(@NotNull final FlowSession sellerSession) {
            //noinspection ConstantConditions
            if (sellerSession == null) throw new NullPointerException("The sellerSession cannot be null");
            this.sellerSession = sellerSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            return subFlow(new ReceiveFinalityFlow(sellerSession));
        }
    }

}
