package com.template.contracts

import com.template.contracts.TokenContractK.Companion.TOKEN_CONTRACT_ID
import com.template.states.TokenStateK
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test

class TokenContractIssueTestsK {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US")).party
    private val carly = TestIdentity(CordaX500Name("Carly", "New York", "US")).party

    @Test
    fun `transaction must include a TokenContract command`() {
        ledgerServices.transaction {
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            // Let's add a command from an unrelated dummy contract.
            command(alice.owningKey, DummyContract.Commands.Create())
            `fails with`("Required com.template.contracts.TokenContractK.Commands command")
            command(alice.owningKey, TokenContractK.Commands.Issue())
            verifies()
        }
    }

    @Test
    fun `Issue transaction must have no inputs`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 10L))
            command(alice.owningKey, TokenContractK.Commands.Issue())
            `fails with`("No tokens should be consumed, in inputs, when issuing.")
        }
    }

    @Test
    fun `Issue transaction must have outputs`() {
        ledgerServices.transaction {
            output(TOKEN_CONTRACT_ID, DummyState())
            command(alice.owningKey, TokenContractK.Commands.Issue())
            `fails with`("There should be issued tokens, in outputs.")
        }
    }

    @Test
    fun `Outputs must not have a zero quantity`() {
        ledgerServices.transaction {
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 0L))
            command(alice.owningKey, TokenContractK.Commands.Issue())
            `fails with`("All quantities must be above 0.")
        }
    }

    @Test
    fun `Outputs must not have negative quantity`() {
        ledgerServices.transaction {
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, -1L))
            command(alice.owningKey, TokenContractK.Commands.Issue())
            `fails with`("All quantities must be above 0.")
        }
    }

    @Test
    fun `Issuer must sign Issue transaction`() {
        ledgerServices.transaction {
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            command(bob.owningKey, TokenContractK.Commands.Issue())
            `fails with`("The issuers should sign.")
        }
    }

    @Test
    fun `All issuers must sign Issue transaction`() {
        ledgerServices.transaction {
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            output(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 20L))
            command(alice.owningKey, TokenContractK.Commands.Issue())
            `fails with`("The issuers should sign.")
        }
    }

    @Test
    fun `Can have different issuers in Issue transaction`() {
        ledgerServices.transaction {
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, alice, 20L))
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 30L))
            output(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 20L))
            output(TOKEN_CONTRACT_ID, TokenStateK(carly, alice, 20L))
            command(listOf(alice.owningKey, carly.owningKey), TokenContractK.Commands.Issue())
            verifies()
        }
    }

}