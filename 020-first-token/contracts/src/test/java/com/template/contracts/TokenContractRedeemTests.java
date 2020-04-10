package com.template.contracts;

import com.template.states.TokenState;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.contracts.DummyContract;
import net.corda.testing.contracts.DummyState;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.util.Arrays;

import static com.template.contracts.TokenContract.TOKEN_CONTRACT_ID;
import static net.corda.testing.node.NodeTestUtils.transaction;

public class TokenContractRedeemTests {
    private final MockServices ledgerServices = new MockServices();
    private final Party alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB")).getParty();
    private final Party bob = new TestIdentity(new CordaX500Name("Bob", "New York", "US")).getParty();
    private final Party carly = new TestIdentity(new CordaX500Name("Carly", "New York", "US")).getParty();

    @Test
    public void transactionMustIncludeATokenContractCommand() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()), new DummyContract.Commands.Create());
            tx.failsWith("Required com.template.contracts.TokenContract.Commands command");
            tx.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()), new TokenContract.Commands.Redeem());
            tx.verifies();
            return null;
        });
    }

    @Test
    public void redeemTransactionMustHaveNoOutputs() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()), new TokenContract.Commands.Redeem());
            tx.failsWith("No tokens should be issued, in outputs, when redeeming.");
            return null;
        });
    }

    @Test
    public void redeemTransactionMustHaveInputs() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new DummyState());
            tx.command(alice.getOwningKey(), new TokenContract.Commands.Redeem());
            tx.failsWith("There should be tokens to redeem, in inputs.");
            return null;
        });
    }

    @Test
    // Testing this may be redundant as these wrong states would have to be issued first, but the contract would not
    // let that happen.
    public void inputsMustNotHaveAZeroQuantity() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 0L));
            tx.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()), new TokenContract.Commands.Redeem());
            tx.failsWith("All quantities must be above 0.");
            return null;
        });
    }

    @Test
    // Testing this may be redundant as these wrong states would have to be issued first, but the contract would not
    // let that happen.
    public void inputsMustNotHaveNegativeQuantity() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, -1L));
            tx.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()), new TokenContract.Commands.Redeem());
            tx.failsWith("All quantities must be above 0.");
            return null;
        });
    }

    @Test
    public void issuerMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.command(bob.getOwningKey(), new TokenContract.Commands.Redeem());
            tx.failsWith("The issuers should sign.");
            return null;
        });
    }

    @Test
    public void currentHolderMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.command(alice.getOwningKey(), new TokenContract.Commands.Redeem());
            tx.failsWith("The current holders should sign.");
            return null;
        });
    }

    @Test
    public void allIssuersMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly, bob, 20L));
            tx.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()), new TokenContract.Commands.Redeem());
            tx.failsWith("The issuers should sign.");
            return null;
        });
    }

    @Test
    public void allCurrentHoldersMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly, bob, 20L));
            tx.command(Arrays.asList(alice.getOwningKey(), carly.getOwningKey()), new TokenContract.Commands.Redeem());
            tx.failsWith("The current holders should sign.");
            return null;
        });
    }

    @Test
    public void canHaveDifferentIssuersInRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, alice, 20L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 30L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly, bob, 20L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly, alice, 20L));
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey(), carly.getOwningKey()),
                    new TokenContract.Commands.Redeem());
            tx.verifies();
            return null;
        });
    }

}