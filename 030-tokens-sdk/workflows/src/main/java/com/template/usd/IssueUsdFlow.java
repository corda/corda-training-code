package com.template.usd;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

class IssueUsdFlow extends FlowLogic<SignedTransaction> {
    @NotNull
    private final Party alice;
    private final long amount;

    public IssueUsdFlow(@NotNull final Party alice, final long amount) {
        this.alice = alice;
        this.amount = amount;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        final TokenType usdTokenType = new TokenType("USD", 2);
        if (!getOurIdentity().getName().equals(UsdTokenConstants.US_MINT)) {
            throw new FlowException("We are not the US Mint");
        }
        final IssuedTokenType usMintUsd = new IssuedTokenType(getOurIdentity(), usdTokenType);

        // Who is going to own the output, and how much?
        // Create a 100$ token that can be split and merged.
        final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(amount, usMintUsd);
        final FungibleToken usdToken = new FungibleToken(amountOfUsd, alice, null);

        // Issue the token to alice.
        return subFlow(new IssueTokens(
                Collections.singletonList(usdToken), // Output instances
                Collections.emptyList())); // Observers
    }
}
