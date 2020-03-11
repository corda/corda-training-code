package com.template.flows

import com.template.states.TokenStateK
import net.corda.testing.node.StartedMockNode
import kotlin.test.assertEquals

fun createFrom(
        issuer: StartedMockNode,
        holder: StartedMockNode,
        quantity: Long) = TokenStateK(
        issuer.info.legalIdentities[0],
        holder.info.legalIdentities[0],
        quantity)

fun TokenStateK.toPair() = Pair(holder, quantity)

fun StartedMockNode.assertHasStatesInVault(
        tokenStates: List<TokenStateK>) {
    val vaultTokens = transaction {
        services.vaultService.queryBy(TokenStateK::class.java).states
    }
    assertEquals(tokenStates.size, vaultTokens.size)
    assertEquals(tokenStates, vaultTokens.map { it.state.data })
}
