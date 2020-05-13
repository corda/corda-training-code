package com.template.proposal.state;

import com.r3.corda.lib.tokens.contracts.FungibleTokenContract;
import com.r3.corda.lib.tokens.contracts.NonFungibleTokenContract;
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.template.dummy.state.DummyState;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.transactions.WireTransaction;
import net.corda.testing.common.internal.ParametersUtilitiesKt;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static com.template.proposal.state.SalesProposalContractTestHelpers.issueToken;
import static net.corda.testing.node.NodeTestUtils.ledger;

public class SalesProposalContractAcceptTests {

    private final TestIdentity notaryId = new TestIdentity(
            new CordaX500Name("Notary", "Washington D.C.", "US"));
    private final MockServices ledgerServices = new MockServices(
            Arrays.asList("com.r3.corda.lib.tokens.contracts", "com.template.proposal.state"),
            notaryId,
            ParametersUtilitiesKt.testNetworkParameters(Collections.emptyList(), 4));
    private final Party usMint = new TestIdentity(
            new CordaX500Name("US Mint", "Washington D.C.", "US")).getParty();
    private final Party dealer = new TestIdentity(
            new CordaX500Name("Dealer", "Dallas", "US")).getParty();
    private final Party alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB")).getParty();
    private final Party bob = new TestIdentity(
            new CordaX500Name("Bob", "New York", "US")).getParty();
    private final TokenType usd = FiatCurrency.Companion.getInstance("USD");
    private final IssuedTokenType mintUsd = new IssuedTokenType(usMint, usd);
    private final IssuedTokenType bobUsd = new IssuedTokenType(bob, usd);
    private final IssuedTokenType carType = new IssuedTokenType(dealer, new TokenType("Car", 0));
    private final Amount<IssuedTokenType> amount1 = new Amount<>(20L, mintUsd);
    private final Amount<IssuedTokenType> amount2 = new Amount<>(25L, mintUsd);
    private final NonFungibleToken aliceNFToken = new NonFungibleToken(
            carType, alice, new UniqueIdentifier(), null);
    private final NonFungibleToken aliceNFToken2 = new NonFungibleToken(
            carType, alice, new UniqueIdentifier(), null);
    private final NonFungibleToken bobNFToken = new NonFungibleToken(
            carType, bob, aliceNFToken.getLinearId(), null);
    private final Instant tenMinutesAway = Instant.now().plus(Duration.ofMinutes(10));

    @Test
    public void thereShouldBeASingleSalesProposalInput() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(bob.getOwningKey()), new SalesProposalContract.Commands.Accept());
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new MoveTokenCommand(carType, Collections.singletonList(0), Collections.singletonList(0)));
                tx.input(aliceIssueTx.outRef(0).getRef());
                tx.output(NonFungibleTokenContract.Companion.getContractId(), bobNFToken);
                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new MoveTokenCommand(mintUsd, Collections.singletonList(1), Collections.singletonList(1)));
                tx.input(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, bob, null));
                tx.output(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, alice, null));
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));

                tx.tweak(txCopy -> {
                    txCopy.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID, new DummyState(alice, bob));
                    return txCopy.failsWith("There should be a single input sales proposal on accept");
                });

                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount2, tenMinutesAway));
                tx.verifies();

                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount1, tenMinutesAway));
                return tx.failsWith("There should be a single input sales proposal on accept");
            });
            return null;
        });
    }

    @Test
    public void thereShouldBeNoSalesProposalOutputs() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(bob.getOwningKey()), new SalesProposalContract.Commands.Accept());
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new MoveTokenCommand(carType, Collections.singletonList(0), Collections.singletonList(0)));
                tx.input(aliceIssueTx.outRef(0).getRef());
                tx.output(NonFungibleTokenContract.Companion.getContractId(), bobNFToken);
                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new MoveTokenCommand(mintUsd, Collections.singletonList(1), Collections.singletonList(1)));
                tx.input(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, bob, null));
                tx.output(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, alice, null));
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount2, tenMinutesAway));
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));
                tx.verifies();

                return tx.tweak(txCopy -> {
                    txCopy.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                            new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                    bob, amount2, tenMinutesAway));
                    return txCopy.failsWith("There should be no sales proposal outputs on accept");
                });
            });
            return null;
        });
    }

    @Test
    public void theAssetShouldBeInInput() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(bob.getOwningKey()), new SalesProposalContract.Commands.Accept());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount2, tenMinutesAway));
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new MoveTokenCommand(carType, Collections.singletonList(2), Collections.singletonList(1)));
                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new MoveTokenCommand(mintUsd, Collections.singletonList(1), Collections.singletonList(0)));
                tx.input(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, bob, null));
                tx.output(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, alice, null));
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));

                tx.tweak(txCopy -> {
                    txCopy.output(NonFungibleTokenContract.Companion.getContractId(), aliceNFToken2);
                    txCopy.input(NonFungibleTokenContract.Companion.getContractId(), aliceNFToken2);
                    return txCopy.failsWith("The asset should be an input on accept");
                });

                tx.output(NonFungibleTokenContract.Companion.getContractId(), bobNFToken);
                tx.input(aliceIssueTx.outRef(0).getRef());
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void theAssetShouldBeHeldByBuyerInOutput() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(bob.getOwningKey()), new SalesProposalContract.Commands.Accept());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount2, tenMinutesAway));
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new MoveTokenCommand(carType, Collections.singletonList(2), Collections.singletonList(1)));
                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new MoveTokenCommand(mintUsd, Collections.singletonList(1), Collections.singletonList(0)));
                tx.input(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, bob, null));
                tx.output(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, alice, null));
                tx.input(aliceIssueTx.outRef(0).getRef());
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));

                tx.tweak(txCopy -> {
                    txCopy.output(NonFungibleTokenContract.Companion.getContractId(), aliceNFToken);
                    return txCopy.failsWith("The asset should be held by buyer in output on accept");
                });

                tx.output(NonFungibleTokenContract.Companion.getContractId(), bobNFToken);
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void theSellerShouldBePaidTheAcceptedAmountInTheAcceptedToken() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(bob.getOwningKey()), new SalesProposalContract.Commands.Accept());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount2, tenMinutesAway));
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new MoveTokenCommand(carType, Collections.singletonList(1), Collections.singletonList(0)));
                tx.input(aliceIssueTx.outRef(0).getRef());
                tx.output(NonFungibleTokenContract.Companion.getContractId(), bobNFToken);
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));

                // Wrong issuer type.
                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(bob.getOwningKey()),
                            new MoveTokenCommand(bobUsd, Collections.singletonList(2), Collections.singletonList(1)));
                    txCopy.input(FungibleTokenContract.Companion.getContractId(),
                            new FungibleToken(new Amount<>(25L, bobUsd), bob, null));
                    txCopy.output(FungibleTokenContract.Companion.getContractId(),
                            new FungibleToken(new Amount<>(25L, bobUsd), alice, null));
                    return txCopy.failsWith(
                            "The seller should be paid the agreed amount in the agreed issued token on accept");
                });

                // Too little quantity in output.
                tx.tweak(txCopy -> {
                    tx.command(Collections.singletonList(bob.getOwningKey()),
                            new MoveTokenCommand(mintUsd, Collections.singletonList(2), Arrays.asList(1, 2)));
                    txCopy.input(FungibleTokenContract.Companion.getContractId(),
                            new FungibleToken(new Amount<>(25L, bobUsd), bob, null));
                    txCopy.output(FungibleTokenContract.Companion.getContractId(),
                            new FungibleToken(new Amount<>(5L, bobUsd), bob, null));
                    txCopy.output(FungibleTokenContract.Companion.getContractId(),
                            new FungibleToken(new Amount<>(20L, bobUsd), alice, null));
                    return txCopy.failsWith(
                            "The seller should be paid the agreed amount in the agreed issued token on accept");
                });

                // Too much quantity in output.
                tx.tweak(txCopy -> {
                    tx.command(Collections.singletonList(bob.getOwningKey()),
                            new MoveTokenCommand(mintUsd, Collections.singletonList(2), Arrays.asList(1, 2)));
                    txCopy.input(FungibleTokenContract.Companion.getContractId(),
                            new FungibleToken(new Amount<>(30L, bobUsd), bob, null));
                    txCopy.output(FungibleTokenContract.Companion.getContractId(),
                            new FungibleToken(new Amount<>(30L, bobUsd), alice, null));
                    return txCopy.failsWith(
                            "The seller should be paid the agreed amount in the agreed issued token on accept");
                });

                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new MoveTokenCommand(mintUsd, Collections.singletonList(2), Collections.singletonList(1)));
                tx.input(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, bob, null));
                tx.output(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, alice, null));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void thereShouldBeATimeWindow() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(bob.getOwningKey()), new SalesProposalContract.Commands.Accept());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount2, tenMinutesAway));
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new MoveTokenCommand(carType, Collections.singletonList(1), Collections.singletonList(0)));
                tx.input(aliceIssueTx.outRef(0).getRef());
                tx.output(NonFungibleTokenContract.Companion.getContractId(), bobNFToken);
                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new MoveTokenCommand(mintUsd, Collections.singletonList(2), Collections.singletonList(1)));
                tx.input(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, bob, null));
                tx.output(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, alice, null));
                tx.failsWith("There should be a future-bounded time window");

                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void theExpirationDateShouldBeAfterTheTimeWindow() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(bob.getOwningKey()), new SalesProposalContract.Commands.Accept());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount2, tenMinutesAway));
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new MoveTokenCommand(carType, Collections.singletonList(1), Collections.singletonList(0)));
                tx.input(aliceIssueTx.outRef(0).getRef());
                tx.output(NonFungibleTokenContract.Companion.getContractId(), bobNFToken);
                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new MoveTokenCommand(mintUsd, Collections.singletonList(2), Collections.singletonList(1)));
                tx.input(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, bob, null));
                tx.output(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, alice, null));

                tx.tweak(txCopy -> {
                    txCopy.timeWindow(Instant.now(), Duration.ofMinutes(10));
                    return txCopy.failsWith("The buyer time window should be before the expiration date");
                });

                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void theBuyerShouldBeTheSigner() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount2, tenMinutesAway));
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new MoveTokenCommand(carType, Collections.singletonList(1), Collections.singletonList(0)));
                tx.input(aliceIssueTx.outRef(0).getRef());
                tx.output(NonFungibleTokenContract.Companion.getContractId(), bobNFToken);
                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new MoveTokenCommand(mintUsd, Collections.singletonList(2), Collections.singletonList(1)));
                tx.input(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, bob, null));
                tx.output(FungibleTokenContract.Companion.getContractId(), new FungibleToken(amount2, alice, null));
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(alice.getOwningKey()),
                            new SalesProposalContract.Commands.Accept());
                    return txCopy.failsWith("The buyer should be the only signer on accept");
                });

                tx.tweak(txCopy -> {
                    txCopy.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                            new SalesProposalContract.Commands.Accept());
                    return txCopy.failsWith("The buyer should be the only signer on accept");
                });

                tx.command(Collections.singletonList(bob.getOwningKey()), new SalesProposalContract.Commands.Accept());
                return tx.verifies();
            });
            return null;
        });
    }
}