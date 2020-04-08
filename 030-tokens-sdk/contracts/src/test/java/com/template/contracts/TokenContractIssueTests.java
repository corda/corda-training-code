package com.template.contracts;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.FungibleTokenContract;
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.template.states.AirMileType;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.contracts.DummyContract;
import net.corda.testing.contracts.DummyState;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static net.corda.testing.node.NodeTestUtils.transaction;

public class TokenContractIssueTests {
    private final MockServices ledgerServices = new MockServices(ImmutableList.of(
            "com.template.contracts",
            "com.template.states",
            "com.r3.corda.lib.tokens.contracts"));
    private final Party alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB")).getParty();
    private final Party bob = new TestIdentity(new CordaX500Name("Bob", "New York", "US")).getParty();
    private final Party carly = new TestIdentity(new CordaX500Name("Carly", "New York", "US")).getParty();
    private final IssuedTokenType aliceMile = new IssuedTokenType(alice, new AirMileType());
    private final IssuedTokenType carlyMile = new IssuedTokenType(carly, new AirMileType());

    @NotNull
    private FungibleToken create(
            @NotNull final IssuedTokenType tokenType,
            @NotNull final Party holder,
            final long quantity) {
        return new FungibleToken(new Amount<>(quantity, tokenType), holder, AirMileType.getContractAttachment());
    }

    @Test
    public void transactionMustIncludeTheAttachment() {
        transaction(ledgerServices, tx -> {
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
            tx.failsWith("Contract verification failed: Expected to find type jar");

            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.verifies();
            return null;
        });
    }

    @Test
    public void transactionMustIncludeATokenContractCommand() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                // Let's add a command from an unrelated dummy contract.
                txCopy.command(alice.getOwningKey(), new DummyContract.Commands.Create());
                txCopy.failsWith("There must be at least one token command in this transaction");
                return null;
            });
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void issueTransactionMustHaveNoInputs() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
            tx.tweak(txCopy -> {
                txCopy.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
                txCopy.failsWith("There is a token group with no assigned command");
                return null;
            });
            tx.verifies();
            return null;
        });
    }

    @Test
    public void issueTransactionCanHaveNoOutputs() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.output(FungibleTokenContract.Companion.getContractId(), new DummyState());
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void outputsMustNotHaveAZeroQuantity() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                txCopy.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Arrays.asList(0, 1)));
                txCopy.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 0L));
                txCopy.failsWith("You cannot issue tokens with a zero amount");
                return null;
            });
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void outputsMustBeAccountedForInCommand() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 15L));
            tx.tweak(txCopy -> {
                txCopy.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
                txCopy.failsWith("There is a token group with no assigned command");
                return null;
            });
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Arrays.asList(0, 1)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void issuerMustSignIssueTransaction() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                txCopy.command(bob.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
                txCopy.failsWith("The issuer must be the signing party when an amount of tokens are issued");
                return null;
            });
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void allIssuersMustSignIssueTransaction() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 20L));
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Collections.singletonList(0)));
            tx.tweak(txCopy -> {
                txCopy.command(alice.getOwningKey(), new IssueTokenCommand(carlyMile, Collections.singletonList(1)));
                txCopy.failsWith("The issuer must be the signing party when an amount of tokens are issued");
                return null;
            });
            tx.command(carly.getOwningKey(), new IssueTokenCommand(carlyMile, Collections.singletonList(1)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void canHaveDifferentIssuersInIssueTransaction() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, alice, 20L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 30L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 20L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(carlyMile, alice, 20L));
            tx.command(alice.getOwningKey(), new IssueTokenCommand(aliceMile, Arrays.asList(0, 1, 2)));
            tx.command(carly.getOwningKey(), new IssueTokenCommand(carlyMile, Arrays.asList(3, 4)));
            tx.verifies();
            return null;
        });
    }

}