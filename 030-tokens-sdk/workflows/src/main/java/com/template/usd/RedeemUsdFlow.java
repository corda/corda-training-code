package com.template.usd;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemFungibleTokens;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;

import java.util.Collections;

class RedeemUsdFlow extends FlowLogic<SignedTransaction> {
    private final long amount;

    RedeemUsdFlow(final long amount) {
        this.amount = amount;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        final TokenType usdTokenType = FiatCurrency.Companion.getInstance("USD");
        final Party usMint = getServiceHub().getNetworkMapCache().getPeerByLegalName(UsdTokenConstants.US_MINT);
        if (usMint == null) throw new FlowException("No US Mint found");

        // Describe how to find those $ held by Me.
        final QueryCriteria heldByMe = QueryUtilitiesKt.heldTokenAmountCriteria(usdTokenType, getOurIdentity());
        final Amount<TokenType> usdAmount = AmountUtilitiesKt.amount(amount, usdTokenType);

        // Do the redeem
        return subFlow(new RedeemFungibleTokens(
                usdAmount, // How much to redeem
                usMint, // issuer
                Collections.emptyList(), // Observers
                heldByMe, // Criteria to find the inputs
                getOurIdentity())); // change holder
    }
}
