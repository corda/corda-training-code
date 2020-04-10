package com.template.exercise.usd;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.node.MockNetworkNotarySpec;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface UsdTokenCourseHelpers {
    CordaX500Name US_MINT = CordaX500Name.parse("O=US Mint, L=Washington D.C., C=US");

    @NotNull
    static MockNetworkParameters prepareMockNetworkParameters() throws Exception {
        return new MockNetworkParameters()
                .withCordappsForAllNodes(ImmutableList.of(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.selection"),
                        TestCordapp.findCordapp("com.template.states"),
                        TestCordapp.findCordapp("com.template.flows")));
    }
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
