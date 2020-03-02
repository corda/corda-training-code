package com.template.contracts;

import com.template.states.TokenState;
import kotlin.NotImplementedError;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.contracts.DummyContract;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.util.Arrays;

import static com.template.contracts.TokenContract.TOKEN_CONTRACT_ID;
import static net.corda.testing.node.NodeTestUtils.transaction;
import static org.junit.Assert.assertEquals;

public class TokenContractMoveTests {
    private final MockServices ledgerServices = new MockServices();
    private final TestIdentity alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB"));
    private final TestIdentity bob = new TestIdentity(new CordaX500Name("Bob", "New York", "US"));
    private final TestIdentity carly = new TestIdentity(new CordaX500Name("Carly", "New York", "US"));

    @Test
    public void transactionMustIncludeATokenContractCommand() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.command(alice.getPublicKey(), new DummyContract.Commands.Create());
            tx.failsWith("Required com.template.contracts.TokenContract.Commands command");
            tx.command(bob.getPublicKey(), new TokenContract.Commands.Move());
            tx.verifies();
            return null;
        });
    }

    @Test
    public void moveTransactionMustHaveInputs() {
        transaction(ledgerServices, tx -> {
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), carly.getParty(), 10L));
            tx.command(alice.getPublicKey(), new TokenContract.Commands.Move());
            tx.failsWith("There should be tokens to move.");
            return null;
        });
    }

    @Test
    public void moveTransactionMustHaveOutputs() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.command(bob.getPublicKey(), new TokenContract.Commands.Move());
            tx.failsWith("There should be moved tokens.");
            return null;
        });
    }

    @Test
    public void issuerMustBeConservedInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 10L));
            tx.command(bob.getPublicKey(), new TokenContract.Commands.Move());
            tx.failsWith("Consumed and created issuers should be identical.");
            return null;
        });
    }

    @Test
    public void allIssuersMustBeConservedInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 20L));
            tx.command(bob.getPublicKey(), new TokenContract.Commands.Move());
            tx.failsWith("Consumed and created issuers should be identical.");
            return null;
        });
    }

    @Test
    public void sumMustBeConservedInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 15L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 20L));
            tx.command(bob.getPublicKey(), new TokenContract.Commands.Move());
            tx.failsWith("The sum of quantities for each issuer should be conserved.");
            return null;
        });
    }

    @Test
    public void allSumsPerIssuerMustBeConservedInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 15L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 20L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 15L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 30L));
            tx.command(bob.getPublicKey(), new TokenContract.Commands.Move());
            tx.failsWith("The sum of quantities for each issuer should be conserved.");
            return null;
        });
    }

    @Test
    public void sumsThatResultInOverflowAreNotPossibleInMoveTransaction() {
        try {
            transaction(ledgerServices, tx -> {
                tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), Long.MAX_VALUE));
                tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), carly.getParty(), 1L));
                tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 1L));
                tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), carly.getParty(), Long.MAX_VALUE));
                tx.command(Arrays.asList(bob.getPublicKey(), carly.getPublicKey()), new TokenContract.Commands.Move());
                tx.failsWith("The sum of quantities for each issuer should be conserved.");
                return null;
            });
            throw new NotImplementedError("Should not reach here");
        } catch (AssertionError e) {
            assertEquals(ArithmeticException.class, e.getCause().getCause().getClass());
        }
    }

    @Test
    public void currentHolderMustSignMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), carly.getParty(), 10L));
            tx.command(alice.getPublicKey(), new TokenContract.Commands.Move());
            tx.failsWith("The holders should sign.");
            return null;
        });
    }

    @Test
    public void allHoldersMustSignMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), carly.getParty(), 20L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), carly.getParty(), 30L));
            tx.command(bob.getPublicKey(), new TokenContract.Commands.Move());
            tx.failsWith("The holders should sign.");
            return null;
        });
    }

    @Test
    public void canHaveDifferentIssuersInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 20L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), alice.getParty(), 5L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 5L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), carly.getParty(), 20L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), carly.getParty(), 40L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), alice.getParty(), 20L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 20L));
            tx.command(Arrays.asList(bob.getPublicKey(), carly.getPublicKey()), new TokenContract.Commands.Move());
            tx.verifies();
            return null;
        });
    }

}