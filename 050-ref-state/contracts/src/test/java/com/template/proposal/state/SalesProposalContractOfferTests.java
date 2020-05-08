package com.template.proposal.state;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.template.dummy.state.DummyContract;
import com.template.dummy.state.DummyState;
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

import java.util.Arrays;
import java.util.Collections;

import static com.template.proposal.state.SalesProposalContractTestHelpers.issueToken;
import static net.corda.testing.node.NodeTestUtils.ledger;

public class SalesProposalContractOfferTests {

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
    private final AbstractParty alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB")).getParty();
    private final AbstractParty bob = new TestIdentity(
            new CordaX500Name("Bob", "New York", "US")).getParty();
    private final AbstractParty carly = new TestIdentity(
            new CordaX500Name("Carly", "New York", "US")).getParty();
    private final TokenType usd = FiatCurrency.Companion.getInstance("USD");
    private final IssuedTokenType mintUsd = new IssuedTokenType(usMint, usd);
    private final IssuedTokenType carType = new IssuedTokenType(dealer, new TokenType("Car", 0));
    private final Amount<IssuedTokenType> amount1 = new Amount<>(15L, mintUsd);
    private final Amount<IssuedTokenType> amount2 = new Amount<>(20L, mintUsd);
    private final Amount<IssuedTokenType> amount3 = new Amount<>(25L, mintUsd);
    private final NonFungibleToken aliceNFToken = new NonFungibleToken(
            carType, alice, new UniqueIdentifier(), null);
    private final NonFungibleToken bobNFToken = new NonFungibleToken(
            carType, bob, new UniqueIdentifier(), null);
    private final FungibleToken bobFToken = new FungibleToken(amount1, bob, null);

    @Test
    public void thereShouldBeASingleSalesProposalCommand() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new DummyContract.Commands.Create());
                tx.reference(aliceIssueTx.outRef(0).getRef());
                tx.output(
                        SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0), bob, amount2));
                tx.failsWith("There should be a single sales proposal command");

                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new SalesProposalContract.Commands.Offer());
                tx.verifies();

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(bob.getOwningKey()),
                            new SalesProposalContract.Commands.Offer());
                    return txCopy.failsWith("There should be a single sales proposal command");
                });

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(bob.getOwningKey()),
                            new SalesProposalContract.Commands.Accept());
                    return txCopy.failsWith("There should be a single sales proposal command");
                });

                tx.command(Collections.singletonList(bob.getOwningKey()),
                        new SalesProposalContract.Commands.Reject());
                return tx.failsWith("There should be a single sales proposal command");
            });
            return null;
        });
    }

    @Test
    public void thereShouldBeASingleReferenceTokenState() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            final WireTransaction bobIssueTx = issueToken(ledger, usMint, bobFToken);
            ledger.transaction(tx -> {
                tx.command(Collections.singletonList(alice.getOwningKey()), new SalesProposalContract.Commands.Offer());
                tx.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0), bob, amount2));
                tx.failsWith("There should be a single reference input token on offer");

                tx.reference(aliceIssueTx.outRef(0).getRef());
                tx.verifies();

                tx.reference(bobIssueTx.outRef(0).getRef());
                return tx.failsWith("There should be a single reference input token on offer");
            });
            return null;
        });
    }

    @Test
    public void thereShouldBeNoSalesProposalInputs() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID, new DummyState(alice, bob));
                tx.command(Collections.singletonList(alice.getOwningKey()), new SalesProposalContract.Commands.Offer());
                tx.reference(aliceIssueTx.outRef(0).getRef());
                tx.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0), bob, amount2));
                tx.verifies();

                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0), bob, amount2));
                return tx.failsWith("There should be no sales proposal inputs on offer");
            });
            return null;
        });
    }

    @Test
    public void thereShouldBeASingleSalesProposalOutput() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            final WireTransaction bobIssueTx = issueToken(ledger, dealer, bobNFToken);
            ledger.transaction(tx -> {
                tx.input(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID, new DummyState(alice, bob));
                tx.command(Collections.singletonList(alice.getOwningKey()), new SalesProposalContract.Commands.Offer());
                tx.reference(aliceIssueTx.outRef(0).getRef());
                tx.failsWith("There should be a single sales proposal output on offer");

                tx.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0), bob, amount2));
                tx.verifies();

                tx.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), bobIssueTx.outRef(0), carly, amount3));
                return tx.failsWith("There should be a single sales proposal output on offer");
            });
            return null;
        });
    }

    @Test
    public void theRefStateAndAssetShouldMatch() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx1 = issueToken(ledger, dealer, aliceNFToken);
            final WireTransaction aliceIssueTx2 = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.reference(aliceIssueTx1.outRef(0).getRef());
                tx.command(Collections.singletonList(alice.getOwningKey()), new SalesProposalContract.Commands.Offer());

                tx.tweak(txCopy -> {
                    txCopy.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                            new SalesProposal(new UniqueIdentifier(), aliceIssueTx2.outRef(0), carly, amount3));
                    return txCopy.failsWith(
                            "The reference token should match the sales proposal output asset");
                });

                tx.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx1.outRef(0), bob, amount2));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void thePriceShouldNotBeZero() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.reference(aliceIssueTx.outRef(0).getRef());
                tx.command(Collections.singletonList(alice.getOwningKey()), new SalesProposalContract.Commands.Offer());

                tx.tweak(txCopy -> {
                    txCopy.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                            new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0),
                                    bob, new Amount<>(0L, mintUsd)));
                    return txCopy.failsWith("The sales proposal offer price should not be zero");
                });

                tx.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0), bob, amount2));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void theSellerShouldBeTheSigner() {
        ledger(ledgerServices, ledger -> {
            final WireTransaction aliceIssueTx = issueToken(ledger, dealer, aliceNFToken);
            ledger.transaction(tx -> {
                tx.reference(aliceIssueTx.outRef(0).getRef());
                tx.output(SalesProposalContract.SALES_PROPOSAL_CONTRACT_ID,
                        new SalesProposal(new UniqueIdentifier(), aliceIssueTx.outRef(0), bob, amount2));

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(bob.getOwningKey()),
                            new SalesProposalContract.Commands.Offer());
                    return txCopy.failsWith("The seller should be the only signer on the offer");
                });

                tx.tweak(txCopy -> {
                    txCopy.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                            new SalesProposalContract.Commands.Offer());
                    return txCopy.failsWith("The seller should be the only signer on the offer");
                });

                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new SalesProposalContract.Commands.Offer());
                return tx.verifies();
            });
            return null;
        });
    }

}