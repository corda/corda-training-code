package com.template.flows

import com.template.states.TokenStateK
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria


data class StateAccumulator(val sum: Long = 0L, val states: List<StateAndRef<TokenStateK>> = listOf()) {
    fun plusIfSumBelow(maxSum: Long, state: StateAndRef<TokenStateK>) =
            if (maxSum <= sum) this
            else StateAccumulator(
                    Math.addExact(sum, state.state.data.quantity),
                    states.plus(state))
}

fun VaultService.fetchWorthAtLeast(
        tokenSum: TokenStateK,
        notaries: List<Party>,
        criteria: QueryCriteria.VaultQueryCriteria = tokenCriteria(tokenSum, notaries),
        paging: PageSpecification = PageSpecification()): List<StateAndRef<TokenStateK>> {
    if (tokenSum.quantity <= 0) return listOf()
    val pagedStates = queryBy(TokenStateK::class.java, criteria, paging).states
    if (pagedStates.isEmpty()) throw IllegalArgumentException("Not enough states to reach sum.")

    val fetched = pagedStates
            .filter { it.state.data.issuer == tokenSum.issuer }
            .fold(StateAccumulator()) { accumulator, state ->
                accumulator.plusIfSumBelow(tokenSum.quantity, state)
            }
    return if (tokenSum.quantity <= fetched.sum) fetched.states
    else fetched.states.plus(fetchWorthAtLeast(
            tokenSum.copy(quantity = tokenSum.quantity - fetched.sum),
            notaries,
            criteria,
            paging.copy(pageNumber = paging.pageNumber + 1)
    ))
}

fun tokenCriteria(token: TokenStateK, notaries: List<Party>) = QueryCriteria.VaultQueryCriteria()
        .withContractStateTypes(setOf(TokenStateK::class.java))
        .withParticipants(listOf(token.holder))
        .withNotary(notaries)

