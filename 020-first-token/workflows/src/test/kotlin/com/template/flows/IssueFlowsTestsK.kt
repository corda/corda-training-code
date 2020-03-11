package com.template.flows

import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssueFlowsTestsK {
    private val network = MockNetwork(MockNetworkParameters(listOf(
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("com.template.flows"))))
    private val alice = network.createNode()
    private val bob = network.createNode()
    private val carly = network.createNode()
    private val dan = network.createNode()

    init {
        listOf(alice, bob, carly).forEach {
            it.registerInitiatedFlow(IssueFlowsK.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `SignedTransaction returned by the flow is signed by the issuer`() {
        val flow = IssueFlowsK.Initiator(bob.info.singleIdentity(), 10L)
        val future = alice.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifyRequiredSignatures()
    }

    @Test
    fun `flow records a transaction in issuer and holder transaction storages only`() {
        val flow = IssueFlowsK.Initiator(bob.info.singleIdentity(), 10L)
        val future = alice.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both transaction storages.
        for (node in listOf(alice, bob)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
        for (node in listOf(carly, dan)) {
            assertNull(node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `flow records a transaction in issuer and both holder transaction storages`() {
        val flow = IssueFlowsK.Initiator(listOf(
                Pair(bob.info.singleIdentity(), 10L),
                Pair(carly.info.singleIdentity(), 20L)))
        val future = alice.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in transaction storages.
        for (node in listOf(alice, bob, carly)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
        assertNull(dan.services.validatedTransactions.getTransaction(signedTx.id))
    }

    @Test
    fun `recorded transaction has no inputs and a single output, the token state`() {
        val expected = createFrom(alice, bob, 10L)

        val flow = IssueFlowsK.Initiator(expected.holder, expected.quantity)
        val future = alice.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(alice, bob)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)!!
            assertTrue(recordedTx.tx.inputs.isEmpty())
            val txOutputs = recordedTx.tx.outputs
            assertEquals(1, txOutputs.size)
            assertEquals(expected, txOutputs[0].data)
        }
    }

    @Test
    fun `there is 1 correct recorded state`() {
        val expected = createFrom(alice, bob, 10L)

        val flow = IssueFlowsK.Initiator(expected.holder, expected.quantity)
        val future = alice.startFlow(flow)
        network.runNetwork()
        future.get()

        // We check the recorded state in both vaults.
        alice.assertHasStatesInVault(listOf(expected))
        bob.assertHasStatesInVault(listOf(expected))
    }

    @Test
    fun `recorded transaction has no inputs and many outputs, the token states`() {
        val expected1 = createFrom(alice, bob, 10L)
        val expected2 = createFrom(alice, carly, 20L)
        val flow = IssueFlowsK.Initiator(listOf(
                expected1.toPair(),
                expected2.toPair()))
        val future = alice.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in the 3 vaults.
        for (node in listOf(alice, bob, carly)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)!!
            assertTrue(recordedTx.tx.inputs.isEmpty())
            val txOutputs = recordedTx.tx.outputs
            assertEquals(2, txOutputs.size)
            assertEquals(expected1, txOutputs[0].data)
            assertEquals(expected2, txOutputs[1].data)
        }
    }

    @Test
    fun `there are 2 correct recorded states by relevance`() {
        val expected1 = createFrom(alice, bob, 10L)
        val expected2 = createFrom(alice, carly, 20L)

        val flow = IssueFlowsK.Initiator(listOf(
                expected1.toPair(),
                expected2.toPair()))
        val future = alice.startFlow(flow)
        network.runNetwork()
        future.get()

        // We check the recorded state in the 4 vaults.
        alice.assertHasStatesInVault(listOf(expected1, expected2))
        // Notice how bob did not save carly's state.
        bob.assertHasStatesInVault(listOf(expected1))
        carly.assertHasStatesInVault(listOf(expected2))
        dan.assertHasStatesInVault(listOf())
    }

    @Test
    fun `recorded transaction has no inputs and 2 outputs of same holder, the token states`() {
        val expected1 = createFrom(alice, bob, 10L)
        val expected2 = createFrom(alice, bob, 20L)

        val flow = IssueFlowsK.Initiator(listOf(
                expected1.toPair(),
                expected2.toPair()))
        val future = alice.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(alice, bob)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)!!
            assertTrue(recordedTx.tx.inputs.isEmpty())
            val txOutputs = recordedTx.tx.outputs
            assertEquals(2, txOutputs.size)
            assertEquals(expected1, txOutputs[0].data)
            assertEquals(expected2, txOutputs[1].data)
        }
    }


    @Test
    fun `there are 2 correct recorded states again`() {
        val expected1 = createFrom(alice, bob, 10L)
        val expected2 = createFrom(alice, bob, 20L)

        val flow = IssueFlowsK.Initiator(listOf(
                expected1.toPair(),
                expected2.toPair()))
        val future = alice.startFlow(flow)
        network.runNetwork()
        future.get()

        // We check the recorded state in the 4 vaults.
        alice.assertHasStatesInVault(listOf(expected1, expected2))
        bob.assertHasStatesInVault(listOf(expected1, expected2))
        carly.assertHasStatesInVault(listOf())
        dan.assertHasStatesInVault(listOf())
    }

}