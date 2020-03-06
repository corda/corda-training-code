package com.template.contracts

import com.template.contracts.TokenContractK.Companion.TOKEN_CONTRACT_ID
import com.template.states.TokenStateK
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import kotlin.test.assertEquals

class TokenContractMoveTestsK {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US"))
    private val carly = TestIdentity(CordaX500Name("Carly", "New York", "US"))

    @Test
    fun `Transaction must include a TokenContract command`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, 10L))
                command(alice.publicKey, DummyContract.Commands.Create())
                `fails with`("Required com.template.contracts.TokenContractK.Commands command")
                command(bob.publicKey, TokenContractK.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Move transaction must have inputs`() {
        ledgerServices.ledger {
            transaction {
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, 10L))
                command(alice.publicKey, TokenContractK.Commands.Move())
                `fails with`("There should be tokens to move.")
            }
        }
    }

    @Test
    fun `Move transaction must have outputs`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                command(bob.publicKey, TokenContractK.Commands.Move())
                `fails with`("There should be moved tokens.")
            }
        }
    }

    @Test
    fun `Issuer must be conserved in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly.party, bob.party, 10L))
                command(bob.publicKey, TokenContractK.Commands.Move())
                `fails with`("Consumed and created issuers should be identical.")
            }
        }
    }

    @Test
    fun `All issuers must be conserved in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(carly.party, bob.party, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 20L))
                command(bob.publicKey, TokenContractK.Commands.Move())
                `fails with`("Consumed and created issuers should be identical.")
            }
        }
    }

    @Test
    fun `Sum must be conserved in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 15L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 20L))
                command(bob.publicKey, TokenContractK.Commands.Move())
                `fails with`("The sum of quantities for each issuer should be conserved.")
            }
        }
    }

    @Test
    fun `All sums per issuer must be conserved in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 15L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 20L))
                input(TOKEN_CONTRACT_ID, TokenStateK(carly.party, bob.party, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(carly.party, bob.party, 15L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly.party, bob.party, 30L))
                command(bob.publicKey, TokenContractK.Commands.Move())
                `fails with`("The sum of quantities for each issuer should be conserved.")
            }
        }
    }

    @Test
    fun `Sums that result in overflow are not possible in Move transaction`() {
        try {
            ledgerServices.ledger {
                transaction {
                    input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, Long.MAX_VALUE))
                    input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, 1L))
                    output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 1L))
                    output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, Long.MAX_VALUE))
                    command(listOf(bob.publicKey, carly.publicKey), TokenContractK.Commands.Move())
                    verifies()
                }
            }
            throw NotImplementedError("Should not reach here")
        } catch(e: TransactionVerificationException.ContractRejection) {
            assertEquals(ArithmeticException::class, e.cause!!::class)
        }
    }

    @Test
    fun `Current holder must sign Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, 10L))
                command(alice.publicKey, TokenContractK.Commands.Move())
                `fails with`("The current holders should sign.")
            }
        }
    }

    @Test
    fun `All current holders must sign Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, 20L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, 30L))
                command(bob.publicKey, TokenContractK.Commands.Move())
                `fails with`("The current holders should sign.")
            }
        }
    }

    @Test
    fun `Can have different issuers in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 20L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, alice.party, 5L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, bob.party, 5L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice.party, carly.party, 20L))
                input(TOKEN_CONTRACT_ID, TokenStateK(carly.party, carly.party, 40L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly.party, alice.party, 20L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly.party, bob.party, 20L))
                command(listOf(bob.publicKey, carly.publicKey), TokenContractK.Commands.Move())
                verifies()
            }
        }
    }

}