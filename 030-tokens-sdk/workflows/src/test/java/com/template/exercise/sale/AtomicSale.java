package com.template.exercise.sale;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.selection.TokenQueryBy;
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import com.template.exercise.car.CarTokenCourseHelpers;
import com.template.exercise.car.CarTokenType;
import kotlin.Pair;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.r3.corda.lib.tokens.selection.database.config.DatabaseSelectionConfigKt.*;

public interface AtomicSale {

    @InitiatingFlow
    class CarSeller extends FlowLogic<SignedTransaction> {

        @NotNull
        private final TokenPointer<CarTokenType> car;
        @NotNull
        private final Party buyer;
        @NotNull
        private final IssuedTokenType issuedCurrency;

        public CarSeller(@NotNull final TokenPointer<CarTokenType> car, @NotNull final Party buyer, @NotNull IssuedTokenType issuedCurrency) {
            //noinspection ConstantConditions
            if (car == null) throw new NullPointerException("The car cannot be null");
            //noinspection ConstantConditions
            if (buyer == null) throw new NullPointerException("The buyer cannot be null");
            //noinspection ConstantConditions
            if (issuedCurrency == null) throw new NullPointerException("The issuedCurrency cannot be null");
            this.car = car;
            this.buyer = buyer;
            this.issuedCurrency = issuedCurrency;
        }


        @Suspendable
        @Override
        @NotNull
        public SignedTransaction call() throws FlowException {
            // Fetch the latest known state.
            final StateAndRef<CarTokenType> carInfo = car.getPointer().resolve(getServiceHub());
            final long price = carInfo.getState().getData().getPrice();

            final FlowSession buyerSession = initiateFlow(buyer);

            // Send the car information to the buyer. A bit ahead of time so that it can fetch states while we do too.
            subFlow(new SendStateAndRefFlow(buyerSession, Collections.singletonList(carInfo)));

            // Send the currency desired.
            buyerSession.send(issuedCurrency);

            // Prepare the transaction.
            final Party notary = getServiceHub().getIdentityService()
                    .wellKnownPartyFromX500Name(CarTokenCourseHelpers.NOTARY);
            if (notary == null) throw new FlowException("Notary not found: " + CarTokenCourseHelpers.NOTARY);
            final TransactionBuilder txBuilder = new TransactionBuilder(notary);

            // Create a proposal to move the car token to Bob.
            final PartyAndToken carForBuyer = new PartyAndToken(buyer, car);
            final QueryCriteria carOwnedBySeller = QueryUtilitiesKt.heldTokenAmountCriteria(car, getOurIdentity());
            MoveTokensUtilitiesKt.addMoveNonFungibleTokens(txBuilder, getServiceHub(), carForBuyer, carOwnedBySeller);

            // Receive the currency states that will go in input.
            final List<StateAndRef<FungibleToken>> currencyInputs = subFlow(new ReceiveStateAndRefFlow<>(buyerSession));

            // Receive the currency states that will go in output.
            //noinspection unchecked
            final List<FungibleToken> currencyOutputs = buyerSession.receive(List.class).unwrap(it -> it);

            // TODO Make sure these inputs / outputs get us paid at least the car price in the right issued currency.

            // Put those currency states where they belong.
            MoveTokensUtilitiesKt.addMoveTokens(txBuilder, currencyInputs, currencyOutputs);

            // Sign the transaction and send it to buyer for signature.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder,
                    getOurIdentity().getOwningKey());
            SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partSignedTx,
                    Collections.singletonList(buyerSession)));

            // Distribute updates of the evolvable car token.
            subFlow(new UpdateDistributionListFlow(fullySignedTx));

            // Finalise the transaction
            return subFlow(new FinalityFlow(fullySignedTx, Collections.singletonList(buyerSession)));
        }
    }

    @InitiatedBy(CarSeller.class)
    class CarBuyer extends FlowLogic<SignedTransaction> {
        @NotNull
        private final FlowSession sellerSession;

        public CarBuyer(@NotNull final FlowSession sellerSession) {
            this.sellerSession = sellerSession;
        }

        @Suspendable
        @Override
        @NotNull
        public SignedTransaction call() throws FlowException {
            // Receive the car information.
            final List<StateAndRef<CarTokenType>> carInfos = subFlow(new ReceiveStateAndRefFlow<>(sellerSession));
            if (carInfos.size() != 1) throw new FlowException("We expected a single car");
            final StateAndRef<CarTokenType> carInfo = carInfos.get(0);
            final long price = carInfo.getState().getData().getPrice();

            // Receive the currency information.
            final IssuedTokenType issuedCurrency = sellerSession.receive(IssuedTokenType.class).unwrap(it -> it);

            // Assemble the currency states.
            final QueryCriteria heldByMe = QueryUtilitiesKt.heldTokenAmountCriteria(
                    issuedCurrency.getTokenType(), getOurIdentity());
            final QueryCriteria properlyIssued = QueryUtilitiesKt.tokenAmountWithIssuerCriteria(
                    issuedCurrency.getTokenType(), issuedCurrency.getIssuer());
            final Amount<TokenType> priceInCurrency = AmountUtilitiesKt.amount(price, issuedCurrency.getTokenType());
            final PartyAndAmount<TokenType> priceForNewHolder = new PartyAndAmount<>(
                    sellerSession.getCounterparty(), priceInCurrency);
            // Generate the buyer's currency inputs, to be spent, and the outputs, the currency tokens that will be
            // held by Alice.
            final DatabaseTokenSelection tokenSelection = new DatabaseTokenSelection(
                    getServiceHub(), MAX_RETRIES_DEFAULT, RETRY_SLEEP_DEFAULT, RETRY_CAP_DEFAULT, PAGE_SIZE_DEFAULT);
            Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> inputsAndOutputs = tokenSelection.generateMove(
                    Collections.singletonList(new Pair<>(sellerSession.getCounterparty(), priceInCurrency)),
                    getOurIdentity(),
                    new TokenQueryBy(issuedCurrency.getIssuer(), it -> true, heldByMe.and(properlyIssued)),
                    getRunId().getUuid());

            // Send the currency states that will go in input.
            subFlow(new SendStateAndRefFlow(sellerSession, inputsAndOutputs.getFirst()));

            // Send the currency states that will go in output.
            sellerSession.send(inputsAndOutputs.getSecond());

            // Sign the received transaction.
            final SecureHash signedTxId = subFlow(new SignTransactionFlow(sellerSession) {
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    // TODO Make sure this is the transaction we expect: car, price and states we sent.
                }
            }).getId();

            // Finalise the transaction.
            return subFlow(new ReceiveFinalityFlow(sellerSession, signedTxId));
        }
    }
}
