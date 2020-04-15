package com.template.usd;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

class MoveUsdFlow extends FlowLogic<SignedTransaction> {
    @NotNull
    private final Party bob;
    private final long amount;

    MoveUsdFlow(@NotNull final Party bob, final long amount) {
        this.bob = bob;
        this.amount = amount;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        // Prepare what we are talking about.
        final TokenType usdTokenType = FiatCurrency.Companion.getInstance("USD");
        final Party usMint = getServiceHub().getNetworkMapCache().getPeerByLegalName(UsdTokenConstants.US_MINT);
        if (usMint == null) throw new FlowException("No US Mint found");

        // Who is going to own the output, and how much?
        final Amount<TokenType> usdAmount = AmountUtilitiesKt.amount(amount, usdTokenType);
        final PartyAndAmount<TokenType> bobsAmount = new PartyAndAmount<>(bob, usdAmount);

        // Describe how to find those $ held by Me.
        final QueryCriteria issuedByUSMint = QueryUtilitiesKt.tokenAmountWithIssuerCriteria(usdTokenType, usMint);
        final QueryCriteria heldByMe = QueryUtilitiesKt.heldTokenAmountCriteria(usdTokenType, getOurIdentity());

        // Do the move
        return subFlow(new MoveFungibleTokens(
                Collections.singletonList(bobsAmount), // Output instances
                Collections.emptyList(), // Observers
                issuedByUSMint.and(heldByMe), // Criteria to find the inputs
                getOurIdentity())); // change holder
    }
}
