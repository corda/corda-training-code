package com.template.flows

import com.google.common.collect.ImmutableList
import com.template.states.TokenStateK
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveFlowsTestsK {
    private val network = MockNetwork(MockNetworkParameters()
            .withNotarySpecs(ImmutableList.of(MockNetworkNotarySpec(Constants.desiredNotary)))
            .withCordappsForAllNodes(listOf(
                    TestCordapp.findCordapp("com.template.contracts"),
                    TestCordapp.findCordapp("com.template.flows"))))
    private val alice = network.createNode()
    private val bob = network.createNode()
    private val carly = network.createNode()
    private val dan = network.createNode()

    init {
        listOf(alice, bob, carly).forEach {
            it.registerInitiatedFlow(IssueFlowsK.Responder::class.java)
            it.registerInitiatedFlow(RedeemFlowsK.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `SignedTransaction returned by the flow is signed by the holder`() {
        val tokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))

        val flow = MoveFlowsK.Initiator(tokens, listOf(createFrom(alice, carly, 10L)))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()
        tx.verifySignaturesExcept(listOf(alice.info.singleIdentity().owningKey, carly.info.singleIdentity().owningKey))
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by both holders, same issuer`() {
        val tokens = alice.issueTokens(network, listOf(
                NodeHolding(bob, 10L),
                NodeHolding(carly, 20L)))

        val flow = MoveFlowsK.Initiator(tokens, listOf(createFrom(alice, dan, 30L)))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()
        tx.verifySignaturesExcept(alice.info.singleIdentity().owningKey)
    }

    @Test
    fun `flow records a transaction in holder transaction storages only`() {
        val tokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))

        val flow = MoveFlowsK.Initiator(tokens, listOf(createFrom(alice, carly, 10L)))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(bob, carly)) {
            assertEquals(tx, bob.services.validatedTransactions.getTransaction(tx.id))
        }
        for (node in listOf(alice, dan)) {
            assertNull(node.services.validatedTransactions.getTransaction(tx.id))
        }
    }

    @Test
    fun `flow records a transaction in both holders transaction storages, same issuer`() {
        val tokens = alice.issueTokens(network, listOf(
                NodeHolding(bob, 10L),
                NodeHolding(carly, 20L)))

        val flow = MoveFlowsK.Initiator(tokens, listOf(createFrom(alice, dan, 30L)))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()

        // We check the recorded transaction in 3 transaction storages.
        for (node in listOf(bob, carly, dan)) {
            assertEquals(tx, node.services.validatedTransactions.getTransaction(tx.id))
        }
        assertNull(alice.services.validatedTransactions.getTransaction(tx.id))
    }

    @Test
    fun `recorded transaction has a single input and a single output`() {
        val tokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))
        val expectedInput = tokens[0].state.data
        val expectedOutput = createFrom(alice, carly, 10L)

        val flow = MoveFlowsK.Initiator(tokens, listOf(expectedOutput))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(bob, carly)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(tx.id)!!
            val txInputs = recordedTx.tx.inputs
            assertEquals(1, txInputs.size)
            assertEquals(expectedInput, node.services.toStateAndRef<TokenStateK>(txInputs[0]).state.data)
            val txOutputs = recordedTx.tx.outputs
            assertEquals(1, txOutputs.size)
            assertEquals(expectedOutput, txOutputs[0].data as TokenStateK)
        }
        for (node in listOf(alice, dan)) {
            assertNull(node.services.validatedTransactions.getTransaction(tx.id))
        }
    }

    @Test
    fun `recorded transaction has two inputs and 1 output, same issuer`() {
        val tokens = alice.issueTokens(network, listOf(
                NodeHolding(bob, 10L),
                NodeHolding(carly, 20L)))
        val expectedInputs = tokens.map { it.state.data }
        val expectedOutput = createFrom(alice, dan, 30L)

        val flow = MoveFlowsK.Initiator(tokens, listOf(expectedOutput))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()

        // We check the recorded transaction in 3 vaults.
        for (node in listOf(bob, carly, dan)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(tx.id)!!
            val txInputs = recordedTx.tx.inputs
            assertEquals(2, txInputs.size)
            assertEquals(expectedInputs, txInputs.map { node.services.toStateAndRef<TokenStateK>(it).state.data })
            val txOutputs = recordedTx.tx.outputs
            assertEquals(1, txOutputs.size)
            assertEquals(expectedOutput, txOutputs[0].data as TokenStateK)
        }
        alice.services.validatedTransactions.getTransaction(tx.id)
    }

    @Test
    fun `there is one recorded state after move only in recipient, issuer keeps old state`() {
        val issuedTokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))
        val expectedOutput = createFrom(alice, carly, 10L)

        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(expectedOutput))
        val future = bob.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        // We check the states in vaults.
        alice.assertHasStatesInVault(issuedTokens.map { it.state.data })
        bob.assertHasStatesInVault(listOf())
        carly.assertHasStatesInVault(listOf(expectedOutput))
    }

    @Test
    fun `there is one recorded state after move only in recipient, same issuer, issuer keeps old states`() {
        val issuedTokens = alice.issueTokens(network, listOf(
                NodeHolding(bob, 10L),
                NodeHolding(carly, 20L)))
        val expectedOutput = createFrom(alice, dan, 30L)

        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(expectedOutput))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()

        // We check the states in vaults.
        alice.assertHasStatesInVault(issuedTokens.map { it.state.data })
        bob.assertHasStatesInVault(listOf())
        carly.assertHasStatesInVault(listOf())
        dan.assertHasStatesInVault(listOf(expectedOutput))
    }

}