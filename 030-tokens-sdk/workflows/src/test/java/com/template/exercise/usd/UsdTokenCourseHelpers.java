package com.template.exercise.usd;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.node.StartedMockNode;
import org.jetbrains.annotations.NotNull;

public interface UsdTokenCourseHelpers {
    CordaX500Name US_MINT = CordaX500Name.parse("O=US Mint, L=Washington D.C., C=US");

    @NotNull
    static FungibleToken createUsdFungible(
            @NotNull final StartedMockNode issuer,
            @NotNull final StartedMockNode holder,
            final long quantity) {
        final TokenType usdType = new TokenType("USD", 2);
        final IssuedTokenType issued = new IssuedTokenType(issuer.getInfo().getLegalIdentities().get(0), usdType);
        final Amount<IssuedTokenType> amount = AmountUtilitiesKt.amount(quantity, issued);
        return new FungibleToken(amount, holder.getInfo().getLegalIdentities().get(0), null);
    }
}
