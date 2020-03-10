package com.template;

import com.google.common.collect.ImmutableList;
import com.template.flows.IssueFlow;
import com.template.states.TokenState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.TransactionState;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class IssueFlowTests {
    private final MockNetwork network = new MockNetwork(new MockNetworkParameters(ImmutableList.of(
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("com.template.flows")
    )));
    private final StartedMockNode alice = network.createNode();
    private final StartedMockNode bob = network.createNode();
    private final StartedMockNode carly = network.createNode();
    private final StartedMockNode dan = network.createNode();

    public IssueFlowTests() {
        alice.registerInitiatedFlow(IssueFlow.Responder.class);
        bob.registerInitiatedFlow(IssueFlow.Responder.class);
        carly.registerInitiatedFlow(IssueFlow.Responder.class);
        dan.registerInitiatedFlow(IssueFlow.Responder.class);
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
    public void signedTransactionReturnedByTheFlowIsSignedByTheIssuer() throws Exception {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(bob.getInfo().getLegalIdentities().get(0), 10L);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();

        final SignedTransaction signedTx = future.get();
        signedTx.verifyRequiredSignatures();
    }

    @Test
    public void flowRecordsATransactionInIssuerAndHolderTransactionStoragesOnly() throws Exception {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(bob.getInfo().getLegalIdentities().get(0), 10L);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both transaction storages.
        for (StartedMockNode node : ImmutableList.of(alice, bob)) {
            assertEquals(signedTx, node.getServices().getValidatedTransactions().getTransaction(signedTx.getId()));
        }
        for (StartedMockNode node : ImmutableList.of(carly, dan)) {
            assertNull(node.getServices().getValidatedTransactions().getTransaction(signedTx.getId()));
        }
    }

    @Test
    public void flowRecordsATransactionInIssuerAndBothHolderTransactionStorages() throws Exception {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(ImmutableList.of(
                new IssueFlow.Pair<>(bob.getInfo().getLegalIdentities().get(0), 10L),
                new IssueFlow.Pair<>(carly.getInfo().getLegalIdentities().get(0), 20L)));
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction signedTx = future.get();

        // We check the recorded transaction in transaction storages.
        for (StartedMockNode node : ImmutableList.of(alice, bob, carly)) {
            assertEquals(signedTx, node.getServices().getValidatedTransactions().getTransaction(signedTx.getId()));
        }
        assertNull(dan.getServices().getValidatedTransactions().getTransaction(signedTx.getId()));
    }

    @Test
    public void recordedTransactionHasNoInputsAndASingleOutputTheTokenState() throws Exception {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(bob.getInfo().getLegalIdentities().get(0), 10L);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(alice, bob)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(signedTx.getId());
            assertNotNull(recordedTx);
            assertTrue(recordedTx.getTx().getInputs().isEmpty());
            final List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assertEquals(1, txOutputs.size());

            final TokenState recordedState = (TokenState) txOutputs.get(0).getData();
            assertEquals(alice.getInfo().getLegalIdentities().get(0), recordedState.getIssuer());
            assertEquals(bob.getInfo().getLegalIdentities().get(0), recordedState.getHolder());
            assertEquals(10L, recordedState.getQuantity());
        }
    }

    @Test
    public void recordedTransactionHasNoInputsAndManyOutputsTheTokenStates() throws Exception {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(ImmutableList.of(
                new IssueFlow.Pair<>(bob.getInfo().getLegalIdentities().get(0), 10L),
                new IssueFlow.Pair<>(carly.getInfo().getLegalIdentities().get(0), 20L)));
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction signedTx = future.get();

        // We check the recorded transaction in the 3 vaults.
        for (StartedMockNode node : ImmutableList.of(alice, bob, carly)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(signedTx.getId());
            assertNotNull(recordedTx);
            assertTrue(recordedTx.getTx().getInputs().isEmpty());
            final List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assertEquals(2, txOutputs.size());

            final TokenState recordedState1 = (TokenState) txOutputs.get(0).getData();
            assertEquals(alice.getInfo().getLegalIdentities().get(0), recordedState1.getIssuer());
            assertEquals(bob.getInfo().getLegalIdentities().get(0), recordedState1.getHolder());
            assertEquals(10L, recordedState1.getQuantity());

            final TokenState recordedState2 = (TokenState) txOutputs.get(1).getData();
            assertEquals(alice.getInfo().getLegalIdentities().get(0), recordedState1.getIssuer());
            assertEquals(carly.getInfo().getLegalIdentities().get(0), recordedState2.getHolder());
            assertEquals(20L, recordedState2.getQuantity());
        }
    }

    @Test
    public void recordedTransactionHasNoInputsAnd2OutputsOfSameHolderTheTokenStates() throws Exception {
        final IssueFlow.Initiator flow = new IssueFlow.Initiator(ImmutableList.of(
                new IssueFlow.Pair<>(bob.getInfo().getLegalIdentities().get(0), 10L),
                new IssueFlow.Pair<>(bob.getInfo().getLegalIdentities().get(0), 20L)));
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        final SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(alice, bob)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(signedTx.getId());
            assertNotNull(recordedTx);
            assertTrue(recordedTx.getTx().getInputs().isEmpty());
            final List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assertEquals(2, txOutputs.size());

            final TokenState recordedState1 = (TokenState) txOutputs.get(0).getData();
            assertEquals(alice.getInfo().getLegalIdentities().get(0), recordedState1.getIssuer());
            assertEquals(bob.getInfo().getLegalIdentities().get(0), recordedState1.getHolder());
            assertEquals(10, recordedState1.getQuantity());

            final TokenState recordedState2 = (TokenState) txOutputs.get(1).getData();
            assertEquals(alice.getInfo().getLegalIdentities().get(0), recordedState2.getIssuer());
            assertEquals(bob.getInfo().getLegalIdentities().get(0), recordedState2.getHolder());
            assertEquals(20, recordedState2.getQuantity());
        }
    }

}
