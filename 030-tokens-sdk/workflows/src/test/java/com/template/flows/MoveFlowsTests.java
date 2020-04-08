package com.template.flows;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler;
import com.template.flows.MoveFlows.Initiator;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.*;
import net.corda.core.flows.FlowException;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.template.flows.FlowHelpers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MoveFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    private final StartedMockNode carly;
    private final StartedMockNode dan;

    public MoveFlowsTests() throws Exception {
        network = new MockNetwork(prepareMockNetworkParameters());
        alice = network.createNode();
        bob = network.createNode();
        carly = network.createNode();
        dan = network.createNode();
        Arrays.asList(alice, bob, carly, dan).forEach(it ->
                it.registerInitiatedFlow(Initiator.class, MoveTokensFlowHandler.class));
    }

    @Before
    public void setup() {
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Test(expected = FlowException.class)
    public void flowFailsWhenInitiatorIsMissingTransactionsTheyWereNotPartyTo() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(
                alice, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 10L)));
        issuedTokens.addAll(issueTokens(
                carly, network, Collections.singletonList(new FlowHelpers.NodeHolding(dan, 20L))));

        final Initiator flow = new Initiator(issuedTokens, Arrays.asList(
                createFrom(alice, dan, 10L),
                createFrom(carly, bob, 20L)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        try {
            future.get();
        } catch (ExecutionException ex) {
            throw ex.getCause();
        }
    }

    @Test
    public void SignedTransactionReturnedByTheFlowIsSignedByTheHolder() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(
                alice, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 10L)));

        final Initiator flow = new Initiator(issuedTokens, Collections.singletonList(
                createFrom(alice, carly, 10L)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();
        tx.verifySignaturesExcept(Arrays.asList(
                alice.getInfo().getLegalIdentities().get(0).getOwningKey(),
                carly.getInfo().getLegalIdentities().get(0).getOwningKey()));
    }

    @Test
    public void SignedTransactionReturnedByTheFlowIsSignedByBothHoldersSameIssuer() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(alice, network, Arrays.asList(
                new FlowHelpers.NodeHolding(bob, 10L),
                new FlowHelpers.NodeHolding(carly, 20L)));

        final Initiator flow = new Initiator(issuedTokens, Collections.singletonList(
                createFrom(alice, dan, 30L)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();
        tx.verifySignaturesExcept(alice.getInfo().getLegalIdentities().get(0).getOwningKey());
    }

    @Test
    public void flowRecordsATransactionInHolderTransactionStoragesOnly() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(alice, network, Collections.singletonList(
                new FlowHelpers.NodeHolding(bob, 10L)));

        final Initiator flow = new Initiator(issuedTokens, Collections.singletonList(
                createFrom(alice, carly, 10L)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in both transaction storages.
        for (StartedMockNode node : Arrays.asList(bob, carly)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        for (StartedMockNode node : Arrays.asList(alice, dan)) {
            assertNull(node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
    }

    @Test
    public void flowRecordsATransactionInBothHoldersTransactionStoragesSameIssuer() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(alice, network, Arrays.asList(
                new FlowHelpers.NodeHolding(bob, 10L),
                new FlowHelpers.NodeHolding(carly, 20L)));

        final Initiator flow = new Initiator(issuedTokens, Collections.singletonList(
                createFrom(alice, dan, 30L)));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in 3 transaction storages.
        for (StartedMockNode node : Arrays.asList(bob, carly, dan)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        assertNull(alice.getServices().getValidatedTransactions().getTransaction(tx.getId()));
    }

    @Test
    public void recordedTransactionHasASingleInputAndASingleOutput() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(alice, network, Collections.singletonList(
                new FlowHelpers.NodeHolding(bob, 10L)));
        final FungibleToken expectedInput = issuedTokens.get(0).getState().getData();
        final FungibleToken expectedOutput = createFrom(alice, carly, 10L);

        final Initiator flow = new Initiator(issuedTokens, Collections.singletonList(expectedOutput));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : Arrays.asList(bob, carly)) {
            SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            @SuppressWarnings("ConstantConditions")
            List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assertEquals(1, txInputs.size());
            assertEquals(expectedInput, node.getServices().toStateAndRef(txInputs.get(0)).getState().getData());
            List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assertEquals(1, txOutputs.size());
            assertEquals(expectedOutput, txOutputs.get(0).getData());
        }
        for (StartedMockNode node : Arrays.asList(alice, dan)) {
            assertNull(node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
    }

    @Test
    public void recordedTransactionHasTwoInputsAnd1OutputSameIssuer() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(alice, network, Arrays.asList(
                new FlowHelpers.NodeHolding(bob, 10L),
                new FlowHelpers.NodeHolding(carly, 20L)));
        final List<FungibleToken> expectedInputs = issuedTokens.stream()
                .map(it -> it.getState().getData())
                .collect(Collectors.toList());
        final FungibleToken expectedOutput = createFrom(alice, dan, 30L);

        final Initiator flow = new Initiator(issuedTokens, Collections.singletonList(expectedOutput));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        // We check the recorded transaction in 3 vaults.
        for (StartedMockNode node : Arrays.asList(bob, carly, dan)) {
            SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            @SuppressWarnings("ConstantConditions")
            List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assertEquals(2, txInputs.size());
            assertEquals(expectedInputs, txInputs.stream()
                    .map(it -> {
                        try {
                            return node.getServices().toStateAndRef(it).getState().getData();
                        } catch (TransactionResolutionException ex) {
                            throw new RuntimeException(ex);
                        }
                    }).collect(Collectors.toList()));
            List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assertEquals(1, txOutputs.size());
            assertEquals(expectedOutput, txOutputs.get(0).getData());
        }
        alice.getServices().getValidatedTransactions().getTransaction(tx.getId());
    }

    @Test
    public void thereIsOneRecordedStateAfterMoveOnlyInRecipientIssuerKeepsOldState() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(alice, network, Collections.singletonList(
                new FlowHelpers.NodeHolding(bob, 10L)));
        final FungibleToken expectedOutput = createFrom(alice, carly, 10L);

        final Initiator flow = new Initiator(issuedTokens, Collections.singletonList(expectedOutput));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the states in vaults.
        assertHasStatesInVault(alice,
                issuedTokens.stream().map(it -> it.getState().getData()).collect(Collectors.toList()));
        assertHasStatesInVault(bob, Collections.emptyList());
        assertHasStatesInVault(carly, Collections.singletonList(expectedOutput));
    }

    @Test
    public void thereIsOneRecordedStateAfterMoveOnlyInRecipientSameIssuerIssuerKeepsOldStates() throws Throwable {
        final List<StateAndRef<FungibleToken>> issuedTokens = issueTokens(alice, network, Arrays.asList(
                new FlowHelpers.NodeHolding(bob, 10L),
                new FlowHelpers.NodeHolding(carly, 20L)));
        final FungibleToken expectedOutput = createFrom(alice, dan, 30L);

        final Initiator flow = new Initiator(issuedTokens, Collections.singletonList(expectedOutput));
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the states in vaults.
        assertHasStatesInVault(alice,
                issuedTokens.stream().map(it -> it.getState().getData()).collect(Collectors.toList()));
        assertHasStatesInVault(bob, Collections.emptyList());
        assertHasStatesInVault(carly, Collections.emptyList());
        assertHasStatesInVault(dan, Collections.singletonList(expectedOutput));
    }

}