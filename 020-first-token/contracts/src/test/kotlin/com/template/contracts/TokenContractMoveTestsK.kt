package com.template.contracts

import com.template.contracts.TokenContractK.Companion.TOKEN_CONTRACT_ID
import com.template.states.TokenStateK
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import kotlin.test.assertEquals

class TokenContractMoveTestsK {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US")).party
    private val carly = TestIdentity(CordaX500Name("Carly", "New York", "US")).party

    @Test
    fun `Transaction must include a TokenContract command`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 10L))
                command(alice.owningKey, DummyContract.Commands.Create())
                `fails with`("Required com.template.contracts.TokenContractK.Commands command")
                command(bob.owningKey, TokenContractK.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Move transaction must have inputs`() {
        ledgerServices.ledger {
            transaction {
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 10L))
                command(alice.owningKey, TokenContractK.Commands.Move())
                `fails with`("There should be tokens to move.")
            }
        }
    }

    @Test
    fun `Move transaction must have outputs`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("There should be moved tokens.")
            }
        }
    }

    @Test
    // Testing this may be redundant as these wrong states would have to be issued first, but the contract would not
    // let that happen.
    fun `Inputs must not have a zero quantity`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 0L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("All quantities must be above 0.")
            }
        }
    }

    @Test
    // Testing this may be redundant as these wrong states would have to be issued first, but the contract would not
    // let that happen.
    fun `Inputs must not have negative quantity`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, -1L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 9L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("All quantities must be above 0.")
            }
        }
    }

    @Test
    fun `Outputs must not have a zero quantity`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 0L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("All quantities must be above 0.")
            }
        }
    }

    @Test
    fun `Outputs must not have negative quantity`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 11L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, -1L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("All quantities must be above 0.")
            }
        }
    }

    @Test
    fun `Issuer must be conserved in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 10L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("Consumed and created issuers should be identical.")
            }
        }
    }

    @Test
    fun `All issuers must be conserved in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 20L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("Consumed and created issuers should be identical.")
            }
        }
    }

    @Test
    fun `Sum must be conserved in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 15L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 20L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("The sum of quantities for each issuer should be conserved.")
            }
        }
    }

    @Test
    fun `All sums per issuer must be conserved in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 15L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 20L))
                input(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 15L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 30L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("The sum of quantities for each issuer should be conserved.")
            }
        }
    }

    @Test
    fun `Sums that result in overflow are not possible in Move transaction`() {
        try {
            ledgerServices.ledger {
                transaction {
                    input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, Long.MAX_VALUE))
                    input(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 1L))
                    output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 1L))
                    output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, Long.MAX_VALUE))
                    command(listOf(bob.owningKey, carly.owningKey), TokenContractK.Commands.Move())
                    verifies()
                }
            }
            throw NotImplementedError("Should not reach here")
        } catch (e: TransactionVerificationException.ContractRejection) {
            assertEquals(ArithmeticException::class, e.cause!!::class)
        }
    }

    @Test
    fun `Current holder must sign Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 10L))
                command(alice.owningKey, TokenContractK.Commands.Move())
                `fails with`("The current holders should sign.")
            }
        }
    }

    @Test
    fun `All current holders must sign Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 20L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 30L))
                command(bob.owningKey, TokenContractK.Commands.Move())
                `fails with`("The current holders should sign.")
            }
        }
    }

    @Test
    fun `Can have different issuers in Move transaction`() {
        ledgerServices.ledger {
            transaction {
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
                input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 20L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, alice, 5L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 5L))
                output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 20L))
                input(TOKEN_CONTRACT_ID, TokenStateK(carly, carly, 40L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly, alice, 20L))
                output(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 20L))
                command(listOf(bob.owningKey, carly.owningKey), TokenContractK.Commands.Move())
                verifies()
            }
        }
    }

}