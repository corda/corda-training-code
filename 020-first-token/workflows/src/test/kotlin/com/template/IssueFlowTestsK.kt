package com.template

import com.template.states.TokenStateK
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

class IssueFlowTestsK {
    private val network = MockNetwork(MockNetworkParameters(listOf(
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("com.template.flows"))))
    private val alice = network.createNode()
    private val bob = network.createNode()
    private val carly = network.createNode()
    private val dan = network.createNode()

    init {
        listOf(alice, bob, carly).forEach {
            it.registerInitiatedFlow(IssueFlowK.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `SignedTransaction returned by the flow is signed by the issuer`() {
        val flow = IssueFlowK.Initiator(bob.info.singleIdentity(), 10L)
        val future = alice.startFlow(flow)
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifyRequiredSignatures()
    }

    @Test
    fun `flow records a transaction in issuer and holder transaction storages only`() {
        val flow = IssueFlowK.Initiator(bob.info.singleIdentity(), 10L)
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
        val flow = IssueFlowK.Initiator(listOf(
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
        val flow = IssueFlowK.Initiator(bob.info.singleIdentity(), 10L)
        val future = alice.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(alice, bob)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)!!
            assertTrue(recordedTx.tx.inputs.isEmpty())
            val txOutputs = recordedTx.tx.outputs
            assertEquals(1, txOutputs.size)

            val recordedState = txOutputs[0].data as TokenStateK
            assertEquals(alice.info.singleIdentity(), recordedState.issuer)
            assertEquals(bob.info.singleIdentity(), recordedState.holder)
            assertEquals(10L, recordedState.quantity)
        }
    }

    @Test
    fun `recorded transaction has no inputs and many outputs, the token states`() {
        val flow = IssueFlowK.Initiator(listOf(
                Pair(bob.info.singleIdentity(), 10L),
                Pair(carly.info.singleIdentity(), 20L)))
        val future = alice.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in the 3 vaults.
        for (node in listOf(alice, bob, carly)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)!!
            assertTrue(recordedTx.tx.inputs.isEmpty())
            val txOutputs = recordedTx.tx.outputs
            assertEquals(2, txOutputs.size)

            val recordedState1 = txOutputs[0].data as TokenStateK
            assertEquals(alice.info.singleIdentity(), recordedState1.issuer)
            assertEquals(bob.info.singleIdentity(), recordedState1.holder)
            assertEquals(10L, recordedState1.quantity)

            val recordedState2 = txOutputs[1].data as TokenStateK
            assertEquals(alice.info.singleIdentity(), recordedState2.issuer)
            assertEquals(carly.info.singleIdentity(), recordedState2.holder)
            assertEquals(20L, recordedState2.quantity)
        }
    }

    @Test
    fun `recorded transaction has no inputs and 2 outputs of same holder, the token states`() {
        val flow = IssueFlowK.Initiator(listOf(
                Pair(bob.info.singleIdentity(), 10L),
                Pair(bob.info.singleIdentity(), 20L)))
        val future = alice.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in both vaults.
        for (node in listOf(alice, bob)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)!!
            assertTrue(recordedTx.tx.inputs.isEmpty())
            val txOutputs = recordedTx.tx.outputs
            assertEquals(2, txOutputs.size)

            val recordedState1 = txOutputs[0].data as TokenStateK
            assertEquals(alice.info.singleIdentity(), recordedState1.issuer)
            assertEquals(bob.info.singleIdentity(), recordedState1.holder)
            assertEquals(10L, recordedState1.quantity)

            val recordedState2 = txOutputs[1].data as TokenStateK
            assertEquals(alice.info.singleIdentity(), recordedState2.issuer)
            assertEquals(bob.info.singleIdentity(), recordedState2.holder)
            assertEquals(20L, recordedState2.quantity)
        }
    }

}