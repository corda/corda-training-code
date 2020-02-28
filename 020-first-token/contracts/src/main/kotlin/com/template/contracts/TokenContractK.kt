package com.template.contracts

import com.template.states.TokenStateK
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

        when (command.value) {
            is Commands.Mint -> requireThat {
                // Constraints on the shape of the transaction.
                "No tokens should be consumed when minting." using inputs.isEmpty()
                "There should be minted tokens." using outputs.isNotEmpty()

                // Constraints on the minted tokens themselves.
                // The "above 0" constraint is enforced at the constructor level.

                // Constraints on the signers.
                "The issuers should sign." using command.signers.containsAll(outputs.map { it.issuer.owningKey }.distinct())
                // We assume the owners need not sign although they are participants.
            }

            else -> throw IllegalArgumentException("Unknown command ${command.value}.")
        }
    }

    interface Commands : CommandData {
        class Mint : Commands
    }
}
