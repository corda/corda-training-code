package com.template.usd;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

@SuppressWarnings("unused")
public interface IssueCurrencyFlows {

    /**
     * Can be started on the command line with:
     * flow start IssueCurrencyFlows$IssueCurrencyToHolderSimpleFlow currencyCode: USD, quantity: 1000, holder: PartyB
     */
    @SuppressWarnings("unused")
    @StartableByRPC
    class IssueCurrencyToHolderSimpleFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final TokenType currency;
        private final long quantity;
        @NotNull
        private final AbstractParty holder;

        public IssueCurrencyToHolderSimpleFlow(
                @NotNull final String currencyCode,
                final long quantity,
                @NotNull final AbstractParty holder) {
            //noinspection ConstantConditions
            if (currencyCode == null) throw new NullPointerException("The currencyCode cannot be null");
            //noinspection ConstantConditions
            if (holder == null) throw new NullPointerException("The holder cannot be null");
            this.currency = FiatCurrency.Companion.getInstance(currencyCode);
            this.quantity = quantity;
            this.holder = holder;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final FungibleToken toIssue = new FungibleToken(AmountUtilitiesKt.amount(
                    quantity,
                    new IssuedTokenType(getOurIdentity(), currency)),
                    holder, null);
            return subFlow(new IssueTokens(Collections.singletonList(toIssue)));
        }
    }

}