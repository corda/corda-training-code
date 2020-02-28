package com.template

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

// *********
// * State *
// *********
data class ExampleState(val data: String) : ContractState {
    override val participants: List<AbstractParty> = listOf()
}
