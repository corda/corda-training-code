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

class TokenContractRedeemTestsK {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US")).party
    private val carly = TestIdentity(CordaX500Name("Carly", "New York", "US")).party

    @Test
    fun `transaction must include a TokenContract command`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            command(listOf(alice.owningKey, bob.owningKey), DummyContract.Commands.Create())
            `fails with`("Required com.template.contracts.TokenContractK.Commands command")
            command(listOf(alice.owningKey, bob.owningKey), TokenContractK.Commands.Redeem())
            verifies()
        }
    }

    @Test
    fun `Redeem transaction must have no outputs`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            output(TOKEN_CONTRACT_ID, TokenStateK(alice, carly, 10L))
            command(listOf(alice.owningKey, bob.owningKey), TokenContractK.Commands.Redeem())
            `fails with`("No tokens should be issued when redeeming.")
        }
    }

    @Test
    fun `Redeem transaction must have inputs`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, DummyState())
            command(alice.owningKey, TokenContractK.Commands.Redeem())
            `fails with`("There should be tokens to redeem.")
        }
    }

    @Test
    // Testing this may be redundant as these wrong states would have to be issued first, but the contract would not
    // let that happen.
    fun `Inputs must not have a zero quantity`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 0L))
            command(listOf(alice.owningKey, bob.owningKey), TokenContractK.Commands.Redeem())
            `fails with`("All quantities must be above 0.")
        }
    }

    @Test
    // Testing this may be redundant as these wrong states would have to be issued first, but the contract would not
    // let that happen.
    fun `Inputs must not have negative quantity`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, -1L))
            command(listOf(alice.owningKey, bob.owningKey), TokenContractK.Commands.Redeem())
            `fails with`("All quantities must be above 0.")
        }
    }

    @Test
    fun `Issuer must sign Redeem transaction`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            command(bob.owningKey, TokenContractK.Commands.Redeem())
            `fails with`("The issuers should sign.")
        }
    }

    @Test
    fun `Current holder must sign Redeem transaction`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            command(alice.owningKey, TokenContractK.Commands.Redeem())
            `fails with`("The current holders should sign.")
        }
    }

    @Test
    fun `All issuers must sign Redeem transaction`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            input(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 20L))
            command(listOf(alice.owningKey, bob.owningKey), TokenContractK.Commands.Redeem())
            `fails with`("The issuers should sign.")
        }
    }

    @Test
    fun `All current holders must sign Redeem transaction`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            input(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 20L))
            command(listOf(alice.owningKey, carly.owningKey), TokenContractK.Commands.Redeem())
            `fails with`("The current holders should sign.")
        }
    }

    @Test
    fun `Can have different issuers in Redeem transaction`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, alice, 20L))
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 30L))
            input(TOKEN_CONTRACT_ID, TokenStateK(carly, bob, 20L))
            input(TOKEN_CONTRACT_ID, TokenStateK(carly, alice, 20L))
            command(listOf(alice.owningKey, bob.owningKey, carly.owningKey), TokenContractK.Commands.Redeem())
            verifies()
        }
    }

}