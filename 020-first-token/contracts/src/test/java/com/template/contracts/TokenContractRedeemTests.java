package com.template.contracts;

import com.sun.tools.javac.util.List;
import com.template.states.TokenState;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.contracts.DummyContract;
import net.corda.testing.contracts.DummyState;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import static com.template.contracts.TokenContract.TOKEN_CONTRACT_ID;
import static net.corda.testing.node.NodeTestUtils.transaction;

public class TokenContractRedeemTests {
    private final MockServices ledgerServices = new MockServices();
    private final TestIdentity alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB"));
    private final TestIdentity bob = new TestIdentity(new CordaX500Name("Bob", "New York", "US"));
    private final TestIdentity carly = new TestIdentity(new CordaX500Name("Carly", "New York", "US"));

    @Test
    public void transactionMustIncludeATokenContractCommand() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.command(List.of(alice.getPublicKey(), bob.getPublicKey()), new DummyContract.Commands.Create());
            tx.failsWith("Required com.template.contracts.TokenContract.Commands command");
            tx.command(List.of(alice.getPublicKey(), bob.getPublicKey()), new TokenContract.Commands.Redeem());
            tx.verifies();
            return null;
        });
    }

    @Test
    public void redeemTransactionMustHaveNoOutputs() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.output(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.command(List.of(alice.getPublicKey(), bob.getPublicKey()), new TokenContract.Commands.Redeem());
            tx.failsWith("No tokens should be minted when redeeming.");
            return null;
        });
    }

    @Test
    public void redeemTransactionMustHaveInputs() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new DummyState());
            tx.command(alice.getPublicKey(), new TokenContract.Commands.Redeem());
            tx.failsWith("There should be tokens to redeem.");
            return null;
        });
    }

    @Test
    public void issuerMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.command(bob.getPublicKey(), new TokenContract.Commands.Redeem());
            tx.failsWith("The issuers should sign.");
            return null;
        });
    }

    @Test
    public void holderMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.command(alice.getPublicKey(), new TokenContract.Commands.Redeem());
            tx.failsWith("The holders should sign.");
            return null;
        });
    }

    @Test
    public void allIssuersMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 20L));
            tx.command(List.of(alice.getPublicKey(), bob.getPublicKey()), new TokenContract.Commands.Redeem());
            tx.failsWith("The issuers should sign.");
            return null;
        });
    }

    @Test
    public void allHoldersMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 20L));
            tx.command(List.of(alice.getPublicKey(), carly.getPublicKey()), new TokenContract.Commands.Redeem());
            tx.failsWith("The holders should sign.");
            return null;
        });
    }

    @Test
    public void canHaveDifferentIssuersInRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 10L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), alice.getParty(), 20L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(alice.getParty(), bob.getParty(), 30L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), bob.getParty(), 20L));
            tx.input(TOKEN_CONTRACT_ID, new TokenState(carly.getParty(), alice.getParty(), 20L));
            tx.command(
                    List.of(alice.getPublicKey(), bob.getPublicKey(), carly.getPublicKey()),
                    new TokenContract.Commands.Redeem());
            tx.verifies();
            return null;
        });
    }
}