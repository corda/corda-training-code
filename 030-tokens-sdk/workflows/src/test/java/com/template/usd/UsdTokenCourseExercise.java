package com.template.usd;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNodeParameters;
import net.corda.testing.node.StartedMockNode;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.template.usd.UsdTokenCourseHelpers.prepareMockNetworkParameters;
import static org.junit.Assert.assertEquals;

public class UsdTokenCourseExercise {
    private final MockNetwork network;
    private final StartedMockNode usMint;
    private final StartedMockNode alice;
    private final StartedMockNode bob;

    public UsdTokenCourseExercise() {
        network = new MockNetwork(prepareMockNetworkParameters());
        usMint = network.createNode(new MockNodeParameters()
                .withLegalName(UsdTokenConstants.US_MINT));
        alice = network.createNode();
        bob = network.createNode();
    }

    @Before
    public void setup() {
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    private SignedTransaction mintIssues100ToAlice() throws Exception {
        final IssueUsdFlow flow = new IssueUsdFlow(alice.getInfo().getLegalIdentities().get(0), 100L);
        final CordaFuture<SignedTransaction> future = usMint.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    private SignedTransaction aliceMoves50ToBob() throws Exception {
        final MoveUsdFlow flow = new MoveUsdFlow(bob.getInfo().getLegalIdentities().get(0), 50L);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
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
        final List<StateAndRef<FungibleToken>> aliceStates = alice.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(1, aliceStates.size());
        final FungibleToken aliceToken = aliceStates.get(0).getState().getData();
        assertEquals(UsdTokenCourseHelpers.createUsdFungible(usMint, alice, 100L), aliceToken);
    }

    @Test
    public void bobGot50Dollars() throws Exception {
        mintIssues100ToAlice();
        aliceMoves50ToBob();

        // Alice has tokens left
        final List<StateAndRef<FungibleToken>> aliceStates = alice.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(1, aliceStates.size());
        final FungibleToken aliceToken = aliceStates.get(0).getState().getData();
        assertEquals(UsdTokenCourseHelpers.createUsdFungible(usMint, alice, 50L), aliceToken);

        // Bob has tokens
        final List<StateAndRef<FungibleToken>> bobStates = bob.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(1, bobStates.size());
        final FungibleToken bobToken = bobStates.get(0).getState().getData();
        assertEquals(UsdTokenCourseHelpers.createUsdFungible(usMint, bob, 50L), bobToken);
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
        final FungibleToken bobToken = bobStates.get(0).getState().getData();
        assertEquals(UsdTokenCourseHelpers.createUsdFungible(usMint, bob, 25L), bobToken);

        // UsMint has tokens
        final List<StateAndRef<FungibleToken>> mintStates = usMint.getServices().getVaultService()
                .queryBy(FungibleToken.class).getStates();
        assertEquals(0, mintStates.size());
    }

}
