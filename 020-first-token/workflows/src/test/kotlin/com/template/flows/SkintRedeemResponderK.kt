package com.template.flows

import com.template.states.TokenStateK
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.SignedTransaction

object OtherRedeemFlowsK {

    @InitiatedBy(RedeemFlowsK.Initiator::class)
    class SkintResponder(counterpartySession: FlowSession) : RedeemFlowsK.Responder(counterpartySession) {

        companion object {
            const val MAX_QUANTITY = 20L
        }

        override fun responderCheck(stx: SignedTransaction) {
            super.responderCheck(stx)
            val lowEnough = stx.toLedgerTransaction(serviceHub, false)
                    .inputsOfType<TokenStateK>()
                    .filter { it.holder == ourIdentity }
                    .all { it.quantity <= MAX_QUANTITY }
            requireThat {
                "Quantity must not be too high." using lowEnough
            }
        }
    }

}