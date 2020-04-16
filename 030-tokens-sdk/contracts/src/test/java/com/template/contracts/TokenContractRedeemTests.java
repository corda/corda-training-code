package com.template.contracts;

import com.r3.corda.lib.tokens.contracts.FungibleTokenContract;
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.template.states.AirMileType;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.contracts.DummyContract;
import net.corda.testing.contracts.DummyState;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.template.contracts.TokenContractTestHelpers.create;
import static net.corda.testing.node.NodeTestUtils.transaction;

public class TokenContractRedeemTests {
    private final MockServices ledgerServices = new MockServices(Collections.singletonList("com.r3.corda.lib.tokens.contracts"));
    private final Party alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB")).getParty();
    private final Party bob = new TestIdentity(new CordaX500Name("Bob", "New York", "US")).getParty();
    private final Party carly = new TestIdentity(new CordaX500Name("Carly", "New York", "US")).getParty();
    private final IssuedTokenType aliceMile = new IssuedTokenType(alice, new AirMileType());
    private final IssuedTokenType carlyMile = new IssuedTokenType(carly, new AirMileType());

    @Test
    public void transactionMustIncludeTheAttachment() {
        transaction(ledgerServices, tx -> {
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
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
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                tx.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()), new DummyContract.Commands.Create());
                tx.failsWith("There must be at least one token command in this transaction");
                return null;
            });
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void redeemTransactionMustHaveLessInOutputs() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.singletonList(0)));
            tx.tweak(txCopy -> {
                txCopy.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
                txCopy.failsWith("Change shouldn't exceed amount redeemed");
                return null;
            });
            tx.output(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 9L));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void redeemTransactionMayHaveNoOutputs() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), new DummyState());
            tx.command(
                    alice.getOwningKey(),
                    new RedeemTokenCommand(aliceMile, Collections.emptyList(), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void inputsMayHaveAZeroQuantity() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 0L));
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

    @Test
    // Testing this may be redundant as these wrong states would have to be issued first, but the contract would not
    // let that happen.
    public void inputsMustBeAccountedForInCommand() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 1L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                        new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
                txCopy.failsWith("There is a token group with no assigned command");
                return null;
            });
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void issuerMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        bob.getOwningKey(),
                        new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
                txCopy.failsWith("The issuer must be the signing party when an amount of tokens are redeemed");
                return null;
            });
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void currentHolderMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        alice.getOwningKey(),
                        new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
                txCopy.failsWith("Owners of redeemed states must be the signing parties.");
                return null;
            });
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void allIssuersMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 20L));
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Collections.singletonList(0), Collections.emptyList()));
            tx.tweak(txCopy -> {
                txCopy.command(
                        Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                        new RedeemTokenCommand(carlyMile, Collections.singletonList(1), Collections.emptyList()));
                txCopy.failsWith("The issuer must be the signing party when an amount of tokens are redeemed");
                return null;
            });
            tx.command(
                    Arrays.asList(carly.getOwningKey(), bob.getOwningKey()),
                    new RedeemTokenCommand(carlyMile, Collections.singletonList(1), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void allCurrentHoldersMustSignRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, carly, 20L));
            tx.tweak(txCopy -> {
                txCopy.command(
                        Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                        new RedeemTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.emptyList()));
                txCopy.failsWith("Owners of redeemed states must be the signing parties");
                return null;
            });
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey(), carly.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Arrays.asList(0, 1), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

    @Test
    public void canHaveDifferentIssuersInRedeemTransaction() {
        transaction(ledgerServices, tx -> {
            tx.attachment("com.template.contracts", AirMileType.getContractAttachment());
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 10L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, alice, 20L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(aliceMile, bob, 30L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(carlyMile, bob, 20L));
            tx.input(FungibleTokenContract.Companion.getContractId(), create(carlyMile, alice, 20L));
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey(), carly.getOwningKey()),
                    new RedeemTokenCommand(aliceMile, Arrays.asList(0, 1, 2), Collections.emptyList()));
            tx.command(
                    Arrays.asList(alice.getOwningKey(), bob.getOwningKey(), carly.getOwningKey()),
                    new RedeemTokenCommand(carlyMile, Arrays.asList(3, 4), Collections.emptyList()));
            tx.verifies();
            return null;
        });
    }

}