package com.template.contracts

import com.template.states.TokenStateK
import com.template.states.mapSumByIssuer
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class TokenContractK : Contract {
    companion object {
        const val TOKEN_CONTRACT_ID = "com.template.contracts.TokenContractK"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        // This contract does not care about states it has no knowledge about.
        // This will be useful, for instance, when the token is exchanged in a trade.
        val inputs = tx.inputsOfType<TokenStateK>()
        val outputs = tx.outputsOfType<TokenStateK>()
        val hasAllPositiveQuantities = inputs.all { 0 < it.quantity } && outputs.all { 0 < it.quantity }

        when (command.value) {
            is Commands.Issue -> requireThat {
                // Constraints on the shape of the transaction.
                "No tokens should be consumed when issuing." using inputs.isEmpty()
                "There should be issued tokens." using outputs.isNotEmpty()

                // Constraints on the issued tokens themselves.
                "All quantities must be above 0." using hasAllPositiveQuantities

                // Constraints on the signers.
                "The issuers should sign." using command.signers.containsAll(outputs.map { it.issuer.owningKey }.distinct())
                // We assume the holders need not sign although they are participants.
            }

            is Commands.Move -> requireThat {
                // Constraints on the shape of the transaction.
                "There should be tokens to move." using inputs.isNotEmpty()
                "There should be moved tokens." using outputs.isNotEmpty()

                // Constraints on the moved tokens themselves.
                "All quantities must be above 0." using hasAllPositiveQuantities
                val inputSums = inputs.mapSumByIssuer()
                val outputSums = outputs.mapSumByIssuer()
                "Consumed and created issuers should be identical." using (inputSums.keys == outputSums.keys)
                "The sum of quantities for each issuer should be conserved." using inputSums.all { outputSums[it.key] == it.value }

                // Constraints on the signers.
                "The current holders should sign." using command.signers.containsAll(inputs.map { it.holder.owningKey }.distinct())
            }

            is Commands.Redeem -> requireThat {
                // Constraints on the shape of the transaction.
                "There should be tokens to redeem." using inputs.isNotEmpty()
                "No tokens should be issued when redeeming." using outputs.isEmpty()

                // Constraints on the redeemed tokens themselves.
                "All quantities must be above 0." using hasAllPositiveQuantities

                // Constraints on the signers.
                "The issuers should sign." using command.signers.containsAll(inputs.map { it.issuer.owningKey }.distinct())
                "The current holders should sign." using command.signers.containsAll(inputs.map { it.holder.owningKey }.distinct())
            }

            else -> throw IllegalArgumentException("Unknown command ${command.value}.")
        }
    }

    interface Commands : CommandData {
        class Issue : Commands
        class Move : Commands
        class Redeem : Commands
    }
}
