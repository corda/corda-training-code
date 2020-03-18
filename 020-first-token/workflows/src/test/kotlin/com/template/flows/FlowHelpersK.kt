package com.template.flows

import com.template.states.TokenStateK
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import kotlin.test.assertEquals

fun createFrom(
        issuer: StartedMockNode,
        holder: StartedMockNode,
        quantity: Long) = TokenStateK(
        issuer.info.singleIdentity(),
        holder.info.singleIdentity(),
        quantity)

fun TokenStateK.toPair() = Pair(holder, quantity)

fun StartedMockNode.assertHasStatesInVault(tokenStates: List<TokenStateK>) {
    val vaultTokens = transaction {
        services.vaultService.queryBy(TokenStateK::class.java).states
    }
    assertEquals(tokenStates.size, vaultTokens.size)
    assertEquals(tokenStates, vaultTokens.map { it.state.data })
}

class NodeHolding(val holder: StartedMockNode, val quantity: Long) {
    fun toPair() = Pair(holder.info.singleIdentity(), quantity)
}

fun StartedMockNode.issueTokens(network: MockNetwork, nodeHolding: Collection<NodeHolding>) =
        IssueFlowsK.Initiator(nodeHolding.map(NodeHolding::toPair))
                .let { startFlow(it) }
                .also { network.runNetwork() }
                .getOrThrow()
                .toLedgerTransaction(services)
                .outRefsOfType<TokenStateK>()
