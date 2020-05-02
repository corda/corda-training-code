package com.template.proposal.state;

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
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

public class SalesProposalContractRejectTests {

    private final TestIdentity notaryId = new TestIdentity(
            new CordaX500Name("Notary", "Washington D.C.", "US"));
    private final MockServices ledgerServices = new MockServices(
            Arrays.asList("com.r3.corda.lib.tokens.contracts", "com.template.proposal.state"),
            notaryId,
            ParametersUtilitiesKt.testNetworkParameters(Collections.emptyList(), 4));
    private final Party usMint = new TestIdentity(
            new CordaX500Name("US Mint", "Washington D.C.", "US")).getParty();
    private final Party dealer = new TestIdentity(
            new CordaX500Name("Dealer", "Dalla", "US")).getParty();
    private final AbstractParty alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB")).getParty();
    private final AbstractParty bob = new TestIdentity(
            new CordaX500Name("Bob", "New York", "US")).getParty();
    private final AbstractParty carly = new TestIdentity(
            new CordaX500Name("Carly", "New York", "US")).getParty();
    private final TokenType usd = FiatCurrency.Companion.getInstance("USD");
    private final IssuedTokenType mintUsd = new IssuedTokenType(usMint, usd);
    private final IssuedTokenType carType = new IssuedTokenType(dealer, new TokenType("Car", 0));
    private final Amount<IssuedTokenType> amount1 = new Amount<>(20L, mintUsd);
    private final NonFungibleToken aliceNFToken = new NonFungibleToken(
            carType, alice, new UniqueIdentifier(), null);
    private final Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));

    @Test
    public void thereShouldBeASingleInputSalesProposal() {
        ledger(ledgerServices, ledger -> {
            ledger.transaction(tx -> {
                final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new SalesProposalContract.Commands.Reject());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount1, tenMinutesAgo));
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));
                tx.verifies();

                return tx.tweak(txCopy -> {
                    txCopy.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                            new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                    bob, amount1, tenMinutesAgo));
                    return txCopy.failsWith("There should be a single input sales proposal on reject");
                });
            });
            return null;
        });
    }

    @Test
    public void thereShouldBeNoSalesProposalOutputs() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new SalesProposalContract.Commands.Reject());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount1, tenMinutesAgo));
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));
                tx.verifies();

                return tx.tweak(txCopy -> {
                    txCopy.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                            new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                    bob, amount1, tenMinutesAgo));
                    return txCopy.failsWith("There should be no sales proposal outputs on reject");
                });
            });
            return null;
        });
    }

    @Test
    public void theBuyerCanRejectAtAnyTime() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new SalesProposalContract.Commands.Reject());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount1, Instant.now()));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void thereShouldBeATimeWindowForSeller() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new SalesProposalContract.Commands.Reject());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount1, tenMinutesAgo));
                tx.failsWith("There should be a past-bounded time window");

                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void theSellerCanRejectOnlyAfterValidity() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new SalesProposalContract.Commands.Reject());
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount1, tenMinutesAgo));

                tx.tweak(txCopy -> {
                    txCopy.timeWindow(tenMinutesAgo, Duration.ofMinutes(1));
                    return txCopy.failsWith("The seller can reject only after the last validity");
                });

                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void theSellerOrBuyerShouldBeASigner() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                bob, amount1, tenMinutesAgo));
                tx.timeWindow(Instant.now(), Duration.ofMinutes(1));

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(carly.getOwningKey()),
                            new SalesProposalContract.Commands.Reject());
                    return txCopy.failsWith("The seller or the buyer or both should be signers");
                });

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(alice.getOwningKey()),
                            new SalesProposalContract.Commands.Reject());
                    return txCopy.verifies();
                });

                tx.tweak(txCopy -> {
                    txCopy.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                            new SalesProposalContract.Commands.Reject());
                    return txCopy.verifies();
                });

                tx.tweak(txCopy -> {
                    txCopy.command(Arrays.asList(alice.getOwningKey(), carly.getOwningKey()),
                            new SalesProposalContract.Commands.Reject());
                    return txCopy.failsWith("Only the seller or the buyer or both should be signers");
                });

                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new SalesProposalContract.Commands.Reject());
                return tx.verifies();
            });
            return null;
        });
    }

}