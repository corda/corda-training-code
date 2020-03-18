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

class RedeemFlowsTestsK {
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
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()


    @Test
    fun `SignedTransaction returned by the flow is signed by both the issuer and the holder`() {
        val issueFlow = IssueFlowsK.Initiator(bob.info.singleIdentity(), 10L)
        val issueFuture = alice.startFlow(issueFlow)
        network.runNetwork()
        val bobToken = issueFuture.getOrThrow()
                .toLedgerTransaction(bob.services)
                .outRefsOfType<TokenStateK>()
                .single()

        val flow = RedeemFlowsK.Initiator(listOf(bobToken))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()
        signedTx.verifyRequiredSignatures()
    }

}