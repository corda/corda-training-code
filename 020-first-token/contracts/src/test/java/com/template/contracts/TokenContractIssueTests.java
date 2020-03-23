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

public class TokenContractIssueTests {
    private final MockServices ledgerServices = new MockServices();
    private final Party alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB")).getParty();
    private final Party bob = new TestIdentity(new CordaX500Name("Bob", "New York", "US")).getParty();
    private final Party carly = new TestIdentity(new CordaX500Name("Carly", "New York", "US")).getParty();

    @Test
    public void transactionMustIncludeATokenContractCommand() {
        transaction(ledgerServices, tx -> {
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            // Let's add a command from an unrelated dummy contract.
            tx.command(alice.getOwningKey(), new DummyContract.Commands.Create());
            tx.failsWith("Required com.template.contracts.TokenContract.Commands command");
            tx.command(alice.getOwningKey(), new TokenContract.Commands.Issue());
            tx.verifies();
            return null;
        });
    }

    @Test
    public void issueTransactionMustHaveNoInputs() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.command(alice.getOwningKey(), new TokenContract.Commands.Issue());
            tx.failsWith("No tokens should be consumed when issuing.");
            return null;
        });
    }

    @Test
    public void issueTransactionMustHaveOutputs() {
        transaction(ledgerServices, tx -> {
            tx.output(TOKEN_CONTRACT_ID, new DummyState());
            tx.command(alice.getOwningKey(), new TokenContract.Commands.Issue());
            tx.failsWith("There should be issued tokens.");
            return null;
        });
    }

    @Test
    public void outputsMustNotHaveAZeroQuantity() {
        transaction(ledgerServices, tx -> {
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, carly, 0L));
            tx.command(alice.getOwningKey(), new TokenContract.Commands.Issue());
            tx.failsWith("All quantities must be above 0.");
            return null;
        });
    }

    @Test
    public void outputsMustNotHaveANegativeQuantity() {
        transaction(ledgerServices, tx -> {
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, carly, -1L));
            tx.command(alice.getOwningKey(), new TokenContract.Commands.Issue());
            tx.failsWith("All quantities must be above 0.");
            return null;
        });
    }

    @Test
    public void issuerMustSignIssueTransaction() {
        transaction(ledgerServices, tx -> {
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.command(bob.getOwningKey(), new TokenContract.Commands.Issue());
            tx.failsWith("The issuers should sign.");
            return null;
        });
    }

    @Test
    public void allIssuersMustSignIssueTransaction() {
        transaction(ledgerServices, tx -> {
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(carly, bob, 20L));
            tx.command(alice.getOwningKey(), new TokenContract.Commands.Issue());
            tx.failsWith("The issuers should sign.");
            return null;
        });
    }

    @Test
    public void canHaveDifferentIssuersInIssueTransaction() {
        transaction(ledgerServices, tx -> {
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, alice, 20L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice, bob, 30L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(carly, bob, 20L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(carly, alice, 20L));
            tx.command(Arrays.asList(alice.getOwningKey(), carly.getOwningKey()), new TokenContract.Commands.Issue());
            tx.verifies();
            return null;
        });
    }

}