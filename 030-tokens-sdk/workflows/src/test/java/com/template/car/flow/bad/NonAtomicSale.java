package com.template.car.flow.bad;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveNonFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import com.template.car.CarTokenType;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public interface NonAtomicSale {

    @InitiatingFlow
    class CarSeller extends FlowLogic<SignedTransaction> {

        @NotNull
        private final TokenPointer<CarTokenType> car;
        @NotNull
        private final IssuedTokenType issuedCurrency;
        @NotNull
        private final Party buyer;

        public CarSeller(
                @NotNull final TokenPointer<CarTokenType> car,
                @NotNull IssuedTokenType issuedCurrency,
                @NotNull final Party buyer) {
            //noinspection ConstantConditions
            if (car == null) throw new NullPointerException("The car cannot be null");
            //noinspection ConstantConditions
            if (issuedCurrency == null) throw new NullPointerException("The issuedCurrency cannot be null");
            //noinspection ConstantConditions
            if (buyer == null) throw new NullPointerException("The buyer cannot be null");
            this.car = car;
            this.issuedCurrency = issuedCurrency;
            this.buyer = buyer;
        }


        @Suspendable
        @Override
        @NotNull
        public SignedTransaction call() throws FlowException {
            // Recall the price.
            final StateAndRef<CarTokenType> carInfo = car.getPointer().resolve(getServiceHub());
            final long price = carInfo.getState().getData().getPrice();

            // Create a session with the buyer.
            final FlowSession buyerSession = initiateFlow(buyer);

            // Send the car price.
            buyerSession.send(price);

            // Send the currency desired.
            buyerSession.send(issuedCurrency);

            // Receive the payment tx. ReceiveTransactionFlow is the responder flow of a specialised DataVendingFlow
            // that sends a transaction and its history.
            final SignedTransaction payTx = subFlow(new ReceiveTransactionFlow(buyerSession));
            // TODO check we were paid indeed
            // Shall we continue or do we disappear with the money?

            // An exception could also happen here leaving the buyer without their money or car.

            // Move the car to the buyer.
            final PartyAndToken carForBuyer = new PartyAndToken(buyer, car);
            final QueryCriteria myCarCriteria = QueryUtilitiesKt.heldTokenAmountCriteria(
                    car, getOurIdentity());
            return subFlow(new MoveNonFungibleTokens(carForBuyer, Collections.emptyList(), myCarCriteria));
        }
    }

    @InitiatedBy(CarSeller.class)
    class CarBuyer extends FlowLogic<Void> {
        @NotNull
        private final FlowSession sellerSession;

        public CarBuyer(@NotNull final FlowSession sellerSession) {
            this.sellerSession = sellerSession;
        }

        @Suspendable
        @Override
        @NotNull
        public Void call() throws FlowException {
            // Receive the car price.
            final long price = sellerSession.receive(Long.class).unwrap(it -> it);

            // Receive the currency information.
            final IssuedTokenType issuedCurrency = sellerSession.receive(IssuedTokenType.class).unwrap(it -> it);

            // Pay seller.
            final QueryCriteria heldByMe = QueryUtilitiesKt.heldTokenAmountCriteria(
                    issuedCurrency.getTokenType(), getOurIdentity());
            final QueryCriteria properlyIssued = QueryUtilitiesKt.tokenAmountWithIssuerCriteria(
                    issuedCurrency.getTokenType(), issuedCurrency.getIssuer());
            final Amount<TokenType> currencyPrice = AmountUtilitiesKt.amount(price, issuedCurrency.getTokenType());
            final PartyAndAmount<TokenType> amountForSeller = new PartyAndAmount<>(sellerSession.getCounterparty(), currencyPrice);
            final SignedTransaction payTx = subFlow(new MoveFungibleTokens(
                    Collections.singletonList(amountForSeller),
                    Collections.singletonList(sellerSession.getCounterparty()),
                    properlyIssued.and(heldByMe),
                    getOurIdentity()));

            // Inform seller. SendTransactionFlow is a specialised DataVendingFlow that sends a transaction and
            // its history.
            subFlow(new SendTransactionFlow(sellerSession, payTx));

            //Receiving the car is taken care of by the auto-responder of MoveNonFungibleTokens.
            return null;
        }
    }
}
