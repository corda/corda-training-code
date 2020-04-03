package com.template.contracts;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.FungibleTokenContract;
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.template.states.AirMileType;
import kotlin.NotImplementedError;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.contracts.DummyContract;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static net.corda.testing.node.NodeTestUtils.transaction;
import static org.junit.Assert.assertEquals;

public class TokenContractMoveTests {
    private final MockServices ledgerServices = new MockServices(ImmutableList.of("com.r3.corda.lib.tokens.contracts"));
    private final Party alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB")).getParty();
    private final Party bob = new TestIdentity(new CordaX500Name("Bob", "New York", "US")).getParty();
    private final Party carly = new TestIdentity(new CordaX500Name("Carly", "New York", "US")).getParty();
    private final IssuedTokenType aliceMile = new IssuedTokenType(alice, AirMileType.create());
    private final IssuedTokenType carlyMile = new IssuedTokenType(carly, AirMileType.create());

    @NotNull
    private FungibleToken create(
            @NotNull final IssuedTokenType tokenType,
            @NotNull final Party holder,
            final long quantity) {
        return new FungibleToken(new Amount<>(quantity, tokenType), holder, null);
    }

    @Test
    public void transactionMustIncludeATokenContractCommand() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                txCopy.command(alice.getOwningKey(), new DummyContract.Commands.Create());
                txCopy.failsWith("There must be at least one token command in this transaction");
                return null;
            });
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void moveTransactionMustHaveInputs() {
        transaction(ledgerServices, tx -> {
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        carly.getOwningKey(),
                        new MoveTokenCommand(aliceMile, Collections.emptyList(), Collections.singletonList(0)));
                txCopy.failsWith("When moving tokens, there must be input states present");
                return null;
            });
            tx.command(
                    carly.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 10L));
            return null;
        });
    }

    @Test
    public void moveTransactionMustHaveOutputs() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        bob.getOwningKey(),
                        new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
                txCopy.failsWith("When moving tokens, there must be output states present");
                return null;
            });
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 10L));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void inputsMayHaveAZeroQuantity() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 0L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void inputsMustBeAccountedForInCommand() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 1L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 11L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        bob.getOwningKey(),
                        new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
                txCopy.failsWith("There is a token group with no assigned command");
                return null;
            });
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void outputsMustNotHaveAZeroQuantity() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 0L));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Collections.singletonList(0), Arrays.asList(0, 1)));
            tx.failsWith("You cannot create output token amounts with a ZERO amount");
            return null;
        });
    }

    @Test
    public void outputsMustBeAccountedForInCommand() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 11L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 1L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        bob.getOwningKey(),
                        new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
                txCopy.failsWith("There is a token group with no assigned command");
                return null;
            });
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Collections.singletonList(0), Arrays.asList(0, 1)));
            return null;
        });
    }

    @Test
    public void issuerMustBeConservedInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 10L));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
            tx.failsWith("There is a token group with no assigned command");
            return null;
        });
    }

    @Test
    public void allIssuersMustBeConservedInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 20L));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(carlyMile, Collections.singletonList(1), Collections.emptyList()));
            tx.failsWith("In move groups the amount of input tokens MUST EQUAL the amount of output tokens");
            return null;
        });
    }

    @Test
    public void sumMustBeConservedInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 15L));
            tx.tweak(txCopy -> {
                txCopy.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 20L));
                txCopy.command(
                        bob.getOwningKey(),
                        new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.singletonList(0)));
                txCopy.failsWith("In move groups the amount of input tokens MUST EQUAL the amount of output tokens");
                return null;
            });
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 25L));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void allSumsPerIssuerMustBeConservedInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 15L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 20L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 15L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 30L));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.singletonList(0)));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(carlyMile, Arrays.asList(2, 3), Collections.singletonList(1)));
            tx.failsWith("In move groups the amount of input tokens MUST EQUAL the amount of output tokens");
            return null;
        });
    }

    @Test
    public void sumsThatResultInOverflowAreNotPossibleInMoveTransaction() {
        try {
            transaction(ledgerServices, tx -> {
                tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, Long.MAX_VALUE));
                tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 1L));
                tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 1L));
                tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, Long.MAX_VALUE));
                tx.command(
                        Arrays.asList(bob.getOwningKey(), carly.getOwningKey()),
                        new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Arrays.asList(0, 1)));
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
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 10L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        alice.getOwningKey(),
                        new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
                txCopy.failsWith("Required signers does not contain all the current owners of the tokens being moved");
                return null;
            });
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void allCurrentHoldersMustSignMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 20L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 30L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        bob.getOwningKey(),
                        new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.singletonList(0)));
                txCopy.failsWith("Required signers does not contain all the current owners of the tokens being moved");
                return null;
            });
            tx.command(
                    Arrays.asList(bob.getOwningKey(), carly.getOwningKey()),
                    new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.singletonList(0)));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void canHaveDifferentIssuersInMoveTransaction() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 20L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, alice, 5L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 5L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 20L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(carlyMile, carly, 40L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(carlyMile, alice, 20L));
            tx.output(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 20L));
            tx.command(
                    bob.getOwningKey(),
                    new MoveTokenCommand(aliceMile, Arrays.asList(0, 1), Arrays.asList(0, 1, 2)));
            tx.command(
                    carly.getOwningKey(),
                    new MoveTokenCommand(carlyMile, Collections.singletonList(2), Arrays.asList(3, 4)));
            tx.verifies();
            return null;
        });
    }

}