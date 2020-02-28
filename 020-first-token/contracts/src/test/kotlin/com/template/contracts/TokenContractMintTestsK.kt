package com.template.contracts

import com.template.contracts.TokenContractK.Companion.TOKEN_CONTRACT_ID
import com.template.states.TokenState
import com.template.states.TokenStateK
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class TokenContractMintTestsK {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US"))
    private val carly = TestIdentity(CordaX500Name("Carly", "New York", "US"))

    @Test
    fun `transaction must include a TokenContract command`() {
        ledgerServices.ledger {
            transaction {
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                command(alice.publicKey, DummyContract.Commands.Create())
                `fails with`("Required com.template.contracts.TokenContractK.Commands command")
                command(alice.publicKey, TokenContractK.Commands.Mint())
                verifies()
            }
        }
    }

    @Test
    fun `Mint transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, 10L))
                command(alice.publicKey, TokenContractK.Commands.Mint())
                `fails with`("No tokens should be consumed when minting.")
            }
        }
    }

    @Test
    fun `Mint transaction must have outputs`() {
        ledgerServices.ledger {
            transaction {
                output(TOKEN_CONTRACT_ID, DummyState())
                command(alice.publicKey, TokenContractK.Commands.Mint())
                `fails with`("There should be minted tokens.")
            }
        }
    }

    @Test
    fun `Issuer must sign Mint transaction`() {
        ledgerServices.ledger {
            transaction {
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                command(bob.publicKey, TokenContractK.Commands.Mint())
                `fails with`("The issuers should sign.")
            }
        }
    }

    @Test
    fun `All issuers must sign Mint transaction`() {
        ledgerServices.ledger {
            transaction {
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly.party, bob.party, 20L))
                command(alice.publicKey, TokenContractK.Commands.Mint())
                `fails with`("The issuers should sign.")
            }
        }
    }

    @Test
    fun `Can have different issuers in Mint transaction`() {
        ledgerServices.ledger {
            transaction {
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, alice.party, 20L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 30L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly.party, bob.party, 20L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly.party, alice.party, 20L))
                command(listOf(alice.publicKey, carly.publicKey), TokenContractK.Commands.Mint())
                verifies()
            }
        }
    }

}