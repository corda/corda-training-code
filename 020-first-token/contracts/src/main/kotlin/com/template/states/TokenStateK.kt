package com.template.states

import com.template.contracts.ExampleContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(ExampleContract::class)
data class TokenStateK(val issuer: Party, val owner: Party, val amount: Long) : ContractState {

    init {
        require(amount > 0) { "amount must be above 0" }
    }

    override val participants: List<AbstractParty> = listOf(owner)
}
