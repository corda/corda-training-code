package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.ci.workflows.ProvideKeyFlow;
import com.r3.corda.lib.ci.workflows.RequestKeyFlow;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.template.car.state.CarTokenType;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface AtomicSaleAccounts {

    @InitiatingFlow
    class CarSeller extends FlowLogic<SignedTransaction> {

        @NotNull
        private final TokenPointer<CarTokenType> car;
        @NotNull
        private final UUID buyer;
        @NotNull
        private final IssuedTokenType issuedCurrency;

        public CarSeller(
                @NotNull final TokenPointer<CarTokenType> car,
                @NotNull final UUID buyer,
                @NotNull final IssuedTokenType issuedCurrency) {
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
            final AccountService accountService = UtilitiesKt.getAccountService(this);
            // We need to have been informed about any account ahead of time.
            final StateAndRef<AccountInfo> buyerAccount = accountService.accountInfo(buyer);
            if (buyerAccount == null) throw new FlowException("This buyer account is unknown: " + buyer);
            final Party buyerHost = buyerAccount.getState().getData().getHost();
            final FlowSession buyerSession = initiateFlow(buyerHost);
            // The host needs to know which of its accounts is buying.
            buyerSession.send(buyer);
            // Which key to use, let's piggy-back on this session anyway.
            final AnonymousParty buyerParty = subFlow(new RequestKeyFlow(
                    buyerSession, buyerAccount.getState().getData().getIdentifier().getId()));
            // We can now pass off to the safe atomic sale.
            return subFlow(new AtomicSaleAccountsSafe.CarSellerFlow(
                    car, buyerParty, buyerSession, issuedCurrency));
        }
    }

    @SuppressWarnings("unused")
    @InitiatedBy(CarSeller.class)
    class CarBuyer extends FlowLogic<SignedTransaction> {
        @NotNull
        private final FlowSession sellerSession;

        @SuppressWarnings("unused")
        public CarBuyer(@NotNull final FlowSession sellerSession) {
            this.sellerSession = sellerSession;
        }

        @Suspendable
        @Override
        @NotNull
        public SignedTransaction call() throws FlowException {
            // Receive the buyer information
            final UUID buyer = sellerSession.receive(UUID.class).unwrap(it -> it);
            final AccountService accountService = UtilitiesKt.getAccountService(this);
            // We need to have been informed about any account ahead of time.
            final StateAndRef<AccountInfo> buyerAccount = accountService.accountInfo(buyer);
            if (buyerAccount == null) throw new FlowException("This buyer account is unknown: " + buyer);
            if (!buyerAccount.getState().getData().getHost().equals(getOurIdentity()))
                throw new FlowException("We are not this account's host");
            // Send a new key, and keep it here for later verification.
            final AnonymousParty buyerParty = subFlow(new ProvideKeyFlow(sellerSession));
            // We can now pass off to the safe atomic sale.
            return subFlow(new AtomicSaleAccountsSafe.CarBuyerFlow(sellerSession, buyerParty));
        }
    }
}
