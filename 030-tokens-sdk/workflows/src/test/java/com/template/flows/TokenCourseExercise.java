package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokensHandler;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TokenCourseExercise {
    private static final CordaX500Name US_MINT = CordaX500Name.parse("O=US Mint, L=Washington D.C., C=US");
    private final MockNetwork network = new MockNetwork(new MockNetworkParameters()
            .withCordappsForAllNodes(ImmutableList.of(TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"))));
    private final StartedMockNode usMint = network.createNode(new MockNodeParameters()
            .withLegalName(US_MINT));
    private final StartedMockNode alice = network.createNode();
    private final StartedMockNode bob = network.createNode();

    public TokenCourseExercise() {
        Arrays.asList(alice, bob).forEach(it ->
                it.registerInitiatedFlow(IssueTokensHandler.class));

    }

    private static class IssueUsdFlow extends FlowLogic<SignedTransaction> {
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
            if (!getOurIdentity().getName().equals(US_MINT)) {
                throw new FlowException("We are not the US Mint");
            }
            final IssuedTokenType usMintUsd = new IssuedTokenType(getOurIdentity(), usdTokenType);
            final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(amount, usMintUsd);
            final FungibleToken usdToken = new FungibleToken(amountOfUsd, alice, null);
            return subFlow(new IssueTokens(
                    Collections.singletonList(usdToken),
                    Collections.emptyList()));
        }
    }

    private static class MoveUsdFlow extends FlowLogic<SignedTransaction> {
        @NotNull
        private final Party bob;
        private final long amount;

        private MoveUsdFlow(@NotNull final Party bob, final long amount) {
            this.bob = bob;
            this.amount = amount;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            final TokenType usdTokenType = FiatCurrency.Companion.getInstance("USD");
            final Party usMint = getServiceHub().getNetworkMapCache().getPeerByLegalName(US_MINT);
            if (usMint == null) throw new FlowException("No US Mint found");
            final QueryCriteria issuedByUSMint = QueryUtilitiesKt.tokenAmountWithIssuerCriteria(usdTokenType, usMint);
            final QueryCriteria heldByMe = QueryUtilitiesKt.heldTokenAmountCriteria(usdTokenType, getOurIdentity());
            final Amount<TokenType> usdAmount = AmountUtilitiesKt.amount(amount, usdTokenType);
            final PartyAndAmount<TokenType> bobsAmount = new PartyAndAmount<>(bob, usdAmount);
            return subFlow(new MoveFungibleTokens(
                    Collections.singletonList(bobsAmount),
                    Collections.emptyList(),
                    issuedByUSMint.and(heldByMe),
                    getOurIdentity()));
        }
    }

    private static class RedeemUsdFlow extends FlowLogic<SignedTransaction> {
        private final long amount;

        private RedeemUsdFlow(final long amount) {
            this.amount = amount;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            final TokenType usdTokenType = FiatCurrency.Companion.getInstance("USD");
            final Party usMint = getServiceHub().getNetworkMapCache().getPeerByLegalName(US_MINT);
            if (usMint == null) throw new FlowException("No US Mint found");
            final QueryCriteria heldByMe = QueryUtilitiesKt.heldTokenAmountCriteria(usdTokenType, getOurIdentity());
            final Amount<TokenType> usdAmount = AmountUtilitiesKt.amount(amount, usdTokenType);
            return subFlow(new RedeemFungibleTokens(
                    usdAmount,
                    usMint,
                    Collections.emptyList(),
                    heldByMe));
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private SignedTransaction mintIssues100ToAlice() throws Exception {
        final IssueUsdFlow flow = new IssueUsdFlow(alice.getInfo().getLegalIdentities().get(0), 100L);
        final CordaFuture<SignedTransaction> future = usMint.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @SuppressWarnings("UnusedReturnValue")
    private SignedTransaction aliceMoves50ToBob() throws Exception {
        final MoveUsdFlow flow = new MoveUsdFlow(bob.getInfo().getLegalIdentities().get(0), 50L);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @SuppressWarnings("UnusedReturnValue")
    private SignedTransaction bobRedeems25() throws Exception {
        final RedeemUsdFlow flow = new RedeemUsdFlow(25L);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @Test
    public void aliceGot100Dollars() throws Exception {
        mintIssues100ToAlice();

        // Alice has the tokens
        final List<StateAndRef<FungibleToken>> states = alice.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(1, states.size());
    }

    @Test
    public void bobGot50Dollars() throws Exception {
        mintIssues100ToAlice();
        aliceMoves50ToBob();

        // Alice has tokens left
        final List<StateAndRef<FungibleToken>> aliceStates = alice.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(1, aliceStates.size());

        // Bob has tokens
        final List<StateAndRef<FungibleToken>> bobStates = bob.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(1, bobStates.size());
    }

    @Test
    public void bobGot25DollarsLeft() throws Exception {
        mintIssues100ToAlice();
        aliceMoves50ToBob();
        bobRedeems25();

        // Bob has tokens
        final List<StateAndRef<FungibleToken>> bobStates = bob.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(1, bobStates.size());

        // UsMint has tokens
        final List<StateAndRef<FungibleToken>> mintStates = usMint.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(2, mintStates.size());
    }

}
