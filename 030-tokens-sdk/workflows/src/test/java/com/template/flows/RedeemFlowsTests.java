package com.template.flows;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlowHandler;
import com.template.flows.RedeemFlows.Initiator;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.StateRef;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.template.flows.FlowTestHelpers.*;
import static org.junit.Assert.*;

public class RedeemFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    private final StartedMockNode carly;
    private final StartedMockNode dan;

    public RedeemFlowsTests() throws Exception {
        network = new MockNetwork(prepareMockNetworkParameters());
        alice = network.createNode();
        bob = network.createNode();
        carly = network.createNode();
        dan = network.createNode();
        Arrays.asList(alice, bob, carly, dan).forEach(it ->
                it.registerInitiatedFlow(Initiator.class, RedeemTokensFlowHandler.class));
    }

    @Before
    public void setup() {
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Test
    public void SignedTransactionReturnedByTheFlowIsSignedByBothTheIssuerAndTheHolder() throws Throwable {
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowTestHelpers.NodeHolding(bob, 10L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        tx.verifySignaturesExcept(bob.getInfo().getLegalIdentities().get(0).getOwningKey());
        tx.verifySignaturesExcept(alice.getInfo().getLegalIdentities().get(0).getOwningKey());
    }

    @Test
    public void SignedTransactionReturnedByTheFlowIsSignedByBothIssuersAndTheHolder() throws Throwable {
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowTestHelpers.NodeHolding(bob, 10L)));
        tokens.addAll(issueTokens(
                carly, network, Collections.singletonList(new FlowTestHelpers.NodeHolding(bob, 20L))));
        final Initiator flow = new Initiator(Collections.singletonList(tokens.get(0)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        tx.verifySignaturesExcept(Arrays.asList(
                bob.getInfo().getLegalIdentities().get(0).getOwningKey(),
                carly.getInfo().getLegalIdentities().get(0).getOwningKey()));
        tx.verifySignaturesExcept(Arrays.asList(
                alice.getInfo().getLegalIdentities().get(0).getOwningKey(),
                carly.getInfo().getLegalIdentities().get(0).getOwningKey()));
        tx.verifySignaturesExcept(Arrays.asList(
                alice.getInfo().getLegalIdentities().get(0).getOwningKey(),
                bob.getInfo().getLegalIdentities().get(0).getOwningKey()));
    }

    @Test
    public void flowRecordsATransactionInIssuerAndHolderTransactionStoragesOnly() throws Throwable {
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowTestHelpers.NodeHolding(bob, 10L)));

        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in both transaction storages.
        for (StartedMockNode node : Arrays.asList(alice, bob)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        for (StartedMockNode node : Arrays.asList(carly, dan)) {
            assertNull(node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
    }

    @Test
    public void flowRecordsATransactionInBothIssuersAndHolderTransactionStoragesOnly() throws Throwable {
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowTestHelpers.NodeHolding(bob, 10L)));
        tokens.addAll(issueTokens(
                carly, network, Collections.singletonList(new FlowTestHelpers.NodeHolding(bob, 20L))));
        final Initiator flow = new Initiator(Collections.singletonList(tokens.get(0)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in both transaction storages.
        for (StartedMockNode node : Arrays.asList(alice, bob)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        assertNull(dan.getServices().getValidatedTransactions().getTransaction(tx.getId()));
    }

    @Test
    public void flowRecordsATransactionInIssuerAndBothHolderTransactionStorages() throws Throwable {
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Arrays.asList(
                        new FlowTestHelpers.NodeHolding(bob, 10L),
                        new FlowTestHelpers.NodeHolding(carly, 20L)));
        final Initiator flow = new Initiator(Collections.singletonList(tokens.get(0)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in transaction storages.
        for (StartedMockNode node : Arrays.asList(alice, bob)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        assertNull(dan.getServices().getValidatedTransactions().getTransaction(tx.getId()));
    }

    @Test
    public void recordedTransactionHasASingleInputTheFungibleTokenAndNoOutputs() throws Throwable {
        final FungibleToken expected = createFrom(alice, bob, 10L);
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowTestHelpers.NodeHolding(bob, 10L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : Arrays.asList(alice, bob)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            @SuppressWarnings("ConstantConditions") final List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assertEquals(1, txInputs.size());
            assertEquals(expected, node.getServices().toStateAndRef(txInputs.get(0)).getState().getData());
            assertTrue(recordedTx.getTx().getOutputs().isEmpty());
        }
    }

    @Test
    public void thereIsNoRecordedStateAfterRedeem() throws Throwable {
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowTestHelpers.NodeHolding(bob, 10L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the state was consumed in both vaults.
        assertHasStatesInVault(alice, Collections.emptyList());
        assertHasStatesInVault(alice, Collections.emptyList());
    }

    @Test
    public void recordedTransactionHasManyInputsTheFungibleTokensAndNoOutputs() throws Throwable {
        final FungibleToken expected = createFrom(alice, bob, 10L);
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Arrays.asList(
                        new FlowTestHelpers.NodeHolding(bob, 10L),
                        new FlowTestHelpers.NodeHolding(carly, 20L)));
        final Initiator flow = new Initiator(Collections.singletonList(tokens.get(0)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in the 3 vaults.
        for (StartedMockNode node : Arrays.asList(alice, bob)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            @SuppressWarnings("ConstantConditions") final List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assertEquals(1, txInputs.size());
            assertEquals(expected, node.getServices().toStateAndRef(txInputs.get(0)).getState().getData());
            assertTrue(recordedTx.getTx().getOutputs().isEmpty());
        }
    }

    @Test
    public void thereAreNoRecordedStatesAfterRedeem() throws Throwable {
        final FungibleToken expected = createFrom(alice, carly, 20L);
        final List<StateAndRef<FungibleToken>> tokens = issueTokens(
                alice, network, Arrays.asList(
                        new FlowTestHelpers.NodeHolding(bob, 10L),
                        new FlowTestHelpers.NodeHolding(carly, 20L)));
        final Initiator flow = new Initiator(Collections.singletonList(tokens.get(0)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the recorded state in the 4 vaults.
        assertHasStatesInVault(alice, Collections.singletonList(expected));
        assertHasStatesInVault(bob, Collections.emptyList());
        assertHasStatesInVault(carly, Collections.singletonList(expected));
        assertHasStatesInVault(dan, Collections.emptyList());
    }

}