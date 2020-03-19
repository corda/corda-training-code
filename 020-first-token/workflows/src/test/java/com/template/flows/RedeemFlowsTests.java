package com.template.flows;

import com.google.common.collect.ImmutableList;
import com.template.flows.RedeemFlows.Initiator;
import com.template.flows.RedeemFlows.SimpleInitiator;
import com.template.states.TokenState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.StateRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.*;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.SignatureException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.template.flows.FlowHelpers.*;
import static net.corda.core.contracts.ContractsDSL.requireThat;
import static org.junit.Assert.*;

public class RedeemFlowsTests {
    private final MockNetwork network = new MockNetwork(new MockNetworkParameters()
            .withNotarySpecs(ImmutableList.of(new MockNetworkNotarySpec(Constants.desiredNotary)))
            .withCordappsForAllNodes(Arrays.asList(
                    TestCordapp.findCordapp("com.template.contracts"),
                    TestCordapp.findCordapp("com.template.flows"))));
    private final StartedMockNode alice = network.createNode();
    private final StartedMockNode bob = network.createNode();
    private final StartedMockNode carly = network.createNode();
    private final StartedMockNode dan = network.createNode();

    public RedeemFlowsTests() {
        Arrays.asList(alice, bob, carly).forEach(it -> {
            it.registerInitiatedFlow(IssueFlows.Responder.class);
            it.registerInitiatedFlow(RedeemFlows.Responder.class);
        });
        dan.registerInitiatedFlow(IssueFlows.Responder.class);
        dan.registerInitiatedFlow(SkintResponder.class);
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
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 10L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();

        tx.verifySignaturesExcept(bob.getInfo().getLegalIdentities().get(0).getOwningKey());
        tx.verifySignaturesExcept(alice.getInfo().getLegalIdentities().get(0).getOwningKey());
    }

    @Test
    public void SignedTransactionReturnedByTheFlowIsSignedByBothIssuersAndTheHolder() throws Throwable {
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 10L)));
        tokens.addAll(issueTokens(
                carly, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 20L))));
        final Initiator flow = new Initiator(tokens);
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
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 10L)));

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
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 10L)));
        tokens.addAll(issueTokens(
                carly, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 20L))));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        // We check the recorded transaction in both transaction storages.
        for (StartedMockNode node : Arrays.asList(alice, bob, carly)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        assertNull(dan.getServices().getValidatedTransactions().getTransaction(tx.getId()));
    }

    @Test
    public void flowRecordsATransactionInIssuerAndBothHolderTransactionStorages() throws Throwable {
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Arrays.asList(
                        new FlowHelpers.NodeHolding(bob, 10L),
                        new FlowHelpers.NodeHolding(carly, 20L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        // We check the recorded transaction in transaction storages.
        for (StartedMockNode node : Arrays.asList(alice, bob, carly)) {
            assertEquals(tx, node.getServices().getValidatedTransactions().getTransaction(tx.getId()));
        }
        assertNull(dan.getServices().getValidatedTransactions().getTransaction(tx.getId()));
    }

    @Test
    public void recordedTransactionHasASingleInputTheTokenStateAndNoOutputs() throws Throwable {
        final TokenState expected = createFrom(alice, bob, 10L);
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 10L)));
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
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Collections.singletonList(new FlowHelpers.NodeHolding(bob, 10L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();

        future.get();
        // We check the state was consumed in both vaults.
        assertHasStatesInVault(alice, Collections.emptyList());
        assertHasStatesInVault(alice, Collections.emptyList());
    }

    @Test
    public void recordedTransactionHasManyInputsTheTokenStatesAndNoOutputs() throws Throwable {
        final TokenState expected1 = createFrom(alice, bob, 10L);
        final TokenState expected2 = createFrom(alice, carly, 20L);
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Arrays.asList(
                        new FlowHelpers.NodeHolding(bob, 10L),
                        new FlowHelpers.NodeHolding(carly, 20L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        // We check the recorded transaction in the 3 vaults.
        for (StartedMockNode node : Arrays.asList(alice, bob, carly)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            @SuppressWarnings("ConstantConditions") final List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assertEquals(2, txInputs.size());
            assertEquals(expected1, node.getServices().toStateAndRef(txInputs.get(0)).getState().getData());
            assertEquals(expected2, node.getServices().toStateAndRef(txInputs.get(1)).getState().getData());
            assertTrue(recordedTx.getTx().getOutputs().isEmpty());
        }
    }

    @Test
    public void thereAreNoRecordedStatesAfterRedeem() throws Throwable {
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Arrays.asList(
                        new FlowHelpers.NodeHolding(bob, 10L),
                        new FlowHelpers.NodeHolding(carly, 20L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();

        future.get();
        // We check the recorded state in the 4 vaults.
        assertHasStatesInVault(alice, Collections.emptyList());
        assertHasStatesInVault(bob, Collections.emptyList());
        assertHasStatesInVault(carly, Collections.emptyList());
        assertHasStatesInVault(dan, Collections.emptyList());
    }

    // The below tests use a specific responder that is defined at the end.

    @Test
    public void SkintHolderResponderLetsPassLowEnoughAmounts() throws Throwable {
        final TokenState expected0 = createFrom(alice, bob, 10L);
        final TokenState expected1 = createFrom(alice, dan, 20L);
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Arrays.asList(
                        new FlowHelpers.NodeHolding(bob, 10L),
                        new FlowHelpers.NodeHolding(dan, 20L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        // We check the recorded transaction in all 3 vaults.
        for (StartedMockNode node : Arrays.asList(alice, bob, dan)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            @SuppressWarnings("ConstantConditions") final List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assertEquals(2, txInputs.size());
            assertEquals(expected0, node.getServices().toStateAndRef(txInputs.get(0)).getState().getData());
            assertEquals(expected1, node.getServices().toStateAndRef(txInputs.get(1)).getState().getData());
            assertTrue(recordedTx.getTx().getOutputs().isEmpty());
        }
    }

    @Test(expected = FlowException.class)
    public void SkintHolderResponderThrowsOnHighAmounts() throws Throwable {
        final List<StateAndRef<TokenState>> tokens = issueTokens(
                alice, network, Arrays.asList(
                        new FlowHelpers.NodeHolding(bob, 10L),
                        new FlowHelpers.NodeHolding(dan, 21L)));
        final Initiator flow = new Initiator(tokens);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        try {
            future.get();
        } catch (ExecutionException ex) {
            throw ex.getCause();
        }
    }

    /**
     * This is an example of another responder that sub-classes the basic Responder.
     */
    @InitiatedBy(Initiator.class)
    static class SkintResponder extends RedeemFlows.Responder {

        private final long MAX_QUANTITY = 20L;

        public SkintResponder(@NotNull FlowSession counterpartySession) {
            super(counterpartySession);
        }

        @Override
        public void additionalChecks(@NotNull final SignedTransaction stx) throws FlowException {
            super.additionalChecks(stx);
            final boolean lowEnough;
            try {
                lowEnough = stx.toLedgerTransaction(getServiceHub(), false)
                        .inputsOfType(TokenState.class)
                        .stream()
                        // It only cares if we are a holder
                        .filter(it -> it.getHolder().equals(getOurIdentity()))
                        // No individual token should have a quantity too high. That's a strange requirement, but it is
                        // here to make a point.
                        .allMatch(it -> it.getQuantity() <= MAX_QUANTITY);
            } catch (SignatureException ex) {
                throw new FlowException(ex);
            }
            requireThat(req -> {
                req.using("Quantity must not be too high.", lowEnough);
                return null;
            });
        }
    }

    @Test
    public void SimpleInitiatorCanCollectThe2ExpectedTokensToRedeem() throws Throwable {
        final TokenState expected0 = createFrom(alice, bob, 10L);
        final TokenState expected1 = createFrom(alice, bob, 20L);
        final List<StateAndRef<TokenState>> tokens = issueTokens(alice, network, Arrays.asList(
                new FlowHelpers.NodeHolding(bob, 10L),
                new FlowHelpers.NodeHolding(bob, 20L),
                new FlowHelpers.NodeHolding(bob, 5L)));

        final SimpleInitiator flow = new SimpleInitiator(
                tokens.get(0).getState().getNotary(),
                alice.getInfo().getLegalIdentities().get(0),
                bob.getInfo().getLegalIdentities().get(0),
                30L);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : Arrays.asList(alice, bob)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            @SuppressWarnings("ConstantConditions") final List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assertEquals(2, txInputs.size());
            assertEquals(expected0, node.getServices().toStateAndRef(txInputs.get(0)).getState().getData());
            assertEquals(expected1, node.getServices().toStateAndRef(txInputs.get(1)).getState().getData());
            assertTrue(recordedTx.getTx().getOutputs().isEmpty());
        }
    }

    @Test
    public void SimpleInitiatorCanCollectThe3ExpectedTokensToRedeem() throws Throwable {
        final TokenState expected0 = createFrom(alice, bob, 10L);
        final TokenState expected1 = createFrom(alice, bob, 20L);
        final TokenState expected2 = createFrom(alice, bob, 5L);
        final List<StateAndRef<TokenState>> tokens = issueTokens(alice, network, Arrays.asList(
                new FlowHelpers.NodeHolding(bob, 10L),
                new FlowHelpers.NodeHolding(bob, 20L),
                new FlowHelpers.NodeHolding(bob, 5L)));

        final SimpleInitiator flow = new SimpleInitiator(
                tokens.get(0).getState().getNotary(),
                alice.getInfo().getLegalIdentities().get(0),
                bob.getInfo().getLegalIdentities().get(0),
                35L);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();

        final SignedTransaction tx = future.get();
        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : Arrays.asList(alice, bob)) {
            final SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(tx.getId());
            @SuppressWarnings("ConstantConditions") final List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assertEquals(3, txInputs.size());
            assertEquals(expected0, node.getServices().toStateAndRef(txInputs.get(0)).getState().getData());
            assertEquals(expected1, node.getServices().toStateAndRef(txInputs.get(1)).getState().getData());
            assertEquals(expected2, node.getServices().toStateAndRef(txInputs.get(2)).getState().getData());
            assertTrue(recordedTx.getTx().getOutputs().isEmpty());
        }
    }

    @Test(expected = FlowException.class)
    public void SimpleInitiatorFailsToCollectIfThereAreNotEnoughTokensToRedeem() throws Throwable {
        final List<StateAndRef<TokenState>> tokens = issueTokens(alice, network, Arrays.asList(
                new FlowHelpers.NodeHolding(bob, 10L),
                new FlowHelpers.NodeHolding(bob, 20L)));

        final SimpleInitiator flow = new SimpleInitiator(
                tokens.get(0).getState().getNotary(),
                alice.getInfo().getLegalIdentities().get(0),
                bob.getInfo().getLegalIdentities().get(0),
                35L);
        final CordaFuture<SignedTransaction> future = bob.startFlow(flow);
        network.runNetwork();
        try {
            future.get();
        } catch (ExecutionException ex) {
            throw ex.getCause();
        }
    }

}