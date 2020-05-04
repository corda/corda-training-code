package com.template.proposal.flow;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.car.flow.CarTokenCourseHelpers;
import com.template.car.flow.CarTokenTypeConstants;
import com.template.car.flow.IssueCarToHolderFlows;
import com.template.car.flow.IssueCarTokenTypeFlows.IssueCarTokenTypeFlow;
import com.template.car.flow.UpdateCarTokenTypeFlows.UpdateCarTokenTypeFlow;
import com.template.car.flow.UsdTokenConstants;
import com.template.car.state.CarTokenType;
import com.template.proposal.flow.SalesProposalAcceptFlows.AcceptSimpleFlow;
import com.template.proposal.flow.SalesProposalOfferFlows.OfferSimpleFlow;
import com.template.proposal.state.SalesProposal;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.NotaryException;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNodeParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestClock;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SalesProposalAcceptFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    private final IssuedTokenType usMintUsd;

    public SalesProposalAcceptFlowsTests() {
        network = new MockNetwork(CarTokenCourseHelpers.prepareMockNetworkParameters());
        notary = network.getDefaultNotaryNode();
        usMint = network.createNode(new MockNodeParameters()
                .withLegalName(UsdTokenConstants.US_MINT));
        dmv = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenTypeConstants.DMV));
        bmwDealer = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenTypeConstants.BMW_DEALER));
        alice = network.createNode();
        bob = network.createNode();
        final TokenType usdTokenType = FiatCurrency.Companion.getInstance("USD");
        usMintUsd = new IssuedTokenType(usMint.getInfo().getLegalIdentities().get(0), usdTokenType);
    }

    @Before
    public void setup() {
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @NotNull
    private StateAndRef<AccountInfo> createAccount(
            @NotNull final StartedMockNode host,
            @NotNull final String name) throws Exception {
        final CordaFuture<StateAndRef<? extends AccountInfo>> future = host.startFlow(
                new CreateAccount(name));
        network.runNetwork();
        //noinspection unchecked
        return (StateAndRef<AccountInfo>) future.get();
    }

    @NotNull
    private AnonymousParty requestNewKey(
            @NotNull final StartedMockNode host,
            @NotNull final AccountInfo forWhom) throws Exception {
        final CordaFuture<AnonymousParty> future = host.startFlow(new RequestKeyForAccount(forWhom));
        network.runNetwork();
        return future.get();
    }

    private void informKeys(
            @NotNull final StartedMockNode host,
            @NotNull final List<PublicKey> who,
            @NotNull final List<StartedMockNode> others) throws Exception {
        for (StartedMockNode other : others) {
            final CordaFuture<?> future = host.startFlow(new SyncKeyMappingInitiator(
                    other.getInfo().getLegalIdentities().get(0),
                    who.stream()
                            .distinct()
                            .map(AnonymousParty::new)
                            .collect(Collectors.toList())));
            network.runNetwork();
            future.get();
        }
    }

    @NotNull
    private SignedTransaction createNewBmw(
            @SuppressWarnings("SameParameterValue") @NotNull final String vin,
            @SuppressWarnings("SameParameterValue") @NotNull final String make,
            @NotNull final List<Party> observers) throws Exception {
        final IssueCarTokenTypeFlow flow = new IssueCarTokenTypeFlow(notary.getInfo().getLegalIdentities().get(0),
                vin, make, observers);
        final CordaFuture<SignedTransaction> future = dmv.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @NotNull
    private SignedTransaction issueCarTo(
            @NotNull final TokenPointer<CarTokenType> car,
            @NotNull final AbstractParty holder) throws Exception {
        final IssueCarToHolderFlows.IssueCarToHolderFlow flow = new IssueCarToHolderFlows.IssueCarToHolderFlow(
                car, bmwDealer.getInfo().getLegalIdentities().get(0), holder);
        final CordaFuture<SignedTransaction> future = bmwDealer.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
    @NotNull
    private SignedTransaction updateMileageOn(
            @NotNull final StateAndRef<CarTokenType> carRef,
            @SuppressWarnings("SameParameterValue") final long mileage,
            final long price,
            @NotNull final List<Party> observers) throws Exception {
        final UpdateCarTokenTypeFlow flow = new UpdateCarTokenTypeFlow(carRef, mileage, price, observers);
        final CordaFuture<SignedTransaction> future = dmv.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @Test
    public void buyerAccountCanAcceptSalesProposalEvenAfterItHasChangedMileageAndInformSellerAccount() throws Exception {
        // Seller is on alice.
        final StateAndRef<AccountInfo> seller = createAccount(alice, "carly");
        final AnonymousParty sellerParty = requestNewKey(alice, seller.getState().getData());
        informKeys(alice, Collections.singletonList(sellerParty.getOwningKey()), Collections.singletonList(bmwDealer));
        // Buyer is on bob.
        final StateAndRef<AccountInfo> buyer = createAccount(bob, "dan");
        final AnonymousParty buyerParty = requestNewKey(bob, buyer.getState().getData());
        informKeys(bob, Collections.singletonList(buyerParty.getOwningKey()), Collections.singletonList(alice));
        // The car.
        final StateAndRef<CarTokenType> bmwType = createNewBmw("abc124", "BMW",
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final StateAndRef<NonFungibleToken> bmw1 = issueCarTo(
                bmwType.getState().getData().toPointer(CarTokenType.class),
                sellerParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0);
        // Seller makes an offer.
        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMint.getInfo().getLegalIdentities().get(0), 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final StateAndRef<SalesProposal> proposal = offerFuture.get().getTx().outRef(0);
        // Issue dollars to Buyer.
        final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(20_000L, usMintUsd);
        final FungibleToken usdTokenBob = new FungibleToken(amountOfUsd,
                bob.getInfo().getLegalIdentities().get(0), null);
        final IssueTokens issueFlow = new IssueTokens(
                Collections.singletonList(usdTokenBob),
                Collections.emptyList());
        final CordaFuture<SignedTransaction> issueFuture = usMint.startFlow(issueFlow);
        network.runNetwork();
        issueFuture.get();
        // Dmv changes the car with informing the buyer only.
        updateMileageOn(bmwType, 8_000L, 22_000L,
                Collections.singletonList(bob.getInfo().getLegalIdentities().get(0)));

        // Buyer accepts.
        final AcceptSimpleFlow acceptFlow = new AcceptSimpleFlow(proposal.getState().getData().getLinearId());
        final CordaFuture<SignedTransaction> acceptFuture = bob.startFlow(acceptFlow);
        network.runNetwork();
        final SignedTransaction acceptTx = acceptFuture.get();

        // Alice got the transaction.
        final SignedTransaction savedTx = alice.getServices().getValidatedTransactions()
                .getTransaction(acceptTx.getId());
        //noinspection ConstantConditions
        assertEquals(proposal.getRef(), savedTx.getTx().getInputs().get(0));

        // Alice cannot find the proposal by linear id.
        final List<StateAndRef<SalesProposal>> foundProposals = alice.getServices().getVaultService().queryBy(
                SalesProposal.class,
                new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(proposal.getState().getData().getLinearId().getId())))
                .getStates();
        assertTrue(foundProposals.isEmpty());

        // Alice knows that the car changed hands.
        final List<StateAndRef<NonFungibleToken>> updatedBmws = alice.getServices().getVaultService()
                .queryBy(NonFungibleToken.class, new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(bmw1.getState().getData().getLinearId().getId())))
                .getStates();
        assertTrue(updatedBmws.isEmpty());
        // Alice now knows about the new mileage.
        final List<StateAndRef<CarTokenType>> updatedBmwTypes = alice.getServices().getVaultService()
                .queryBy(CarTokenType.class, new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(bmwType.getState().getData().getLinearId().getId())))
                .getStates();
        assertEquals(1, updatedBmwTypes.size());
        assertEquals(8_000L, updatedBmwTypes.get(0).getState().getData().getMileage());

        // The seller has the money.
        final long sellersMoney = savedTx.getTx().outputsOfType(FungibleToken.class).stream()
                .filter(it -> it.getHolder().equals(sellerParty))
                .filter(it -> it.getIssuedTokenType().equals(usMintUsd))
                .map(it -> it.getAmount().getQuantity())
                .reduce(0L, Math::addExact);
        assertEquals(11_000_00, sellersMoney);
    }

    @Test(expected = NotaryException.class)
    public void buyerAccountCannotAcceptSalesProposalIfItHasChangedMileageWithoutInforming() throws Throwable {
        // Seller is on alice.
        final StateAndRef<AccountInfo> seller = createAccount(alice, "carly");
        final AnonymousParty sellerParty = requestNewKey(alice, seller.getState().getData());
        informKeys(alice, Collections.singletonList(sellerParty.getOwningKey()), Collections.singletonList(bmwDealer));
        // Buyer is on bob.
        final StateAndRef<AccountInfo> buyer = createAccount(bob, "dan");
        final AnonymousParty buyerParty = requestNewKey(bob, buyer.getState().getData());
        informKeys(bob, Collections.singletonList(buyerParty.getOwningKey()), Collections.singletonList(alice));
        // The car.
        final StateAndRef<CarTokenType> bmwType = createNewBmw("abc124", "BMW",
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final StateAndRef<NonFungibleToken> bmw1 = issueCarTo(
                bmwType.getState().getData().toPointer(CarTokenType.class),
                sellerParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0);
        // Seller makes an offer.
        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMint.getInfo().getLegalIdentities().get(0), 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final StateAndRef<SalesProposal> proposal = offerFuture.get().getTx().outRef(0);
        // Issue dollars to Buyer.
        final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(20_000L, usMintUsd);
        final FungibleToken usdTokenBob = new FungibleToken(amountOfUsd,
                bob.getInfo().getLegalIdentities().get(0), null);
        final IssueTokens issueFlow = new IssueTokens(
                Collections.singletonList(usdTokenBob),
                Collections.emptyList());
        final CordaFuture<SignedTransaction> issueFuture = usMint.startFlow(issueFlow);
        network.runNetwork();
        issueFuture.get();
        // Dmv changes the car with informing only the seller.
        updateMileageOn(bmwType, 8_000L, 22_000L,
                Collections.singletonList(alice.getInfo().getLegalIdentities().get(0)));

        // Buyer accepts.
        final AcceptSimpleFlow acceptFlow = new AcceptSimpleFlow(proposal.getState().getData().getLinearId());
        final CordaFuture<SignedTransaction> acceptFuture = bob.startFlow(acceptFlow);
        network.runNetwork();
        try {
            acceptFuture.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test(expected = InsufficientBalanceException.class)
    public void buyerAccountCannotBuyIfDoesNotHaveEnough() throws Throwable {
        // Seller is on alice.
        final StateAndRef<AccountInfo> seller = createAccount(alice, "carly");
        final AnonymousParty sellerParty = requestNewKey(alice, seller.getState().getData());
        informKeys(alice, Collections.singletonList(sellerParty.getOwningKey()), Collections.singletonList(bmwDealer));
        // Buyer is on bob.
        final StateAndRef<AccountInfo> buyer = createAccount(bob, "dan");
        final AnonymousParty buyerParty = requestNewKey(bob, buyer.getState().getData());
        informKeys(bob, Collections.singletonList(buyerParty.getOwningKey()), Collections.singletonList(alice));
        // The car.
        final StateAndRef<CarTokenType> bmwType = createNewBmw("abc124", "BMW",
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final StateAndRef<NonFungibleToken> bmw1 = issueCarTo(
                bmwType.getState().getData().toPointer(CarTokenType.class),
                sellerParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0);
        // Seller makes an offer.
        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMint.getInfo().getLegalIdentities().get(0), 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final StateAndRef<SalesProposal> proposal = offerFuture.get().getTx().outRef(0);
        // Issue not enough dollars to buyer.
        final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(10_000L, usMintUsd);
        final FungibleToken usdTokenBob = new FungibleToken(amountOfUsd,
                bob.getInfo().getLegalIdentities().get(0), null);
        final IssueTokens issueFlow = new IssueTokens(
                Collections.singletonList(usdTokenBob),
                Collections.emptyList());
        final CordaFuture<SignedTransaction> future = usMint.startFlow(issueFlow);
        network.runNetwork();
        future.get();
        // Issue irrelevant dollars to buyer.
        final IssuedTokenType monopoly = new IssuedTokenType(bob.getInfo().getLegalIdentities().get(0),
                FiatCurrency.Companion.getInstance("USD"));
        final Amount<IssuedTokenType> amountOfMonopoly = AmountUtilitiesKt.amount(10_000L, monopoly);
        final FungibleToken monopolyTokenBob = new FungibleToken(amountOfMonopoly,
                bob.getInfo().getLegalIdentities().get(0), null);
        final IssueTokens monopolyFlow = new IssueTokens(
                Collections.singletonList(monopolyTokenBob),
                Collections.emptyList());
        final CordaFuture<SignedTransaction> monopolyFuture = bob.startFlow(monopolyFlow);
        network.runNetwork();
        monopolyFuture.get();

        // Buyer accepts.
        final AcceptSimpleFlow acceptFlow = new AcceptSimpleFlow(proposal.getState().getData().getLinearId());
        final CordaFuture<SignedTransaction> acceptFuture = bob.startFlow(acceptFlow);
        network.runNetwork();
        try {
            acceptFuture.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test(expected = NotaryException.class)
    public void buyerCannotAcceptSalesAfterExpiration() throws Throwable {
        // Seller is on alice.
        final StateAndRef<AccountInfo> seller = createAccount(alice, "carly");
        final AnonymousParty sellerParty = requestNewKey(alice, seller.getState().getData());
        informKeys(alice, Collections.singletonList(sellerParty.getOwningKey()), Collections.singletonList(bmwDealer));
        // Buyer is on bob.
        final StateAndRef<AccountInfo> buyer = createAccount(bob, "dan");
        final AnonymousParty buyerParty = requestNewKey(bob, buyer.getState().getData());
        informKeys(bob, Collections.singletonList(buyerParty.getOwningKey()), Collections.singletonList(alice));
        // The car.
        final StateAndRef<CarTokenType> bmwType = createNewBmw("abc124", "BMW",
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final StateAndRef<NonFungibleToken> bmw1 = issueCarTo(
                bmwType.getState().getData().toPointer(CarTokenType.class),
                sellerParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0);
        // Seller makes an offer.
        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMint.getInfo().getLegalIdentities().get(0), 10);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final StateAndRef<SalesProposal> proposal = offerFuture.get().getTx().outRef(0);
        // Issue dollars to Buyer.
        final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(20_000L, usMintUsd);
        final FungibleToken usdTokenBob = new FungibleToken(amountOfUsd,
                bob.getInfo().getLegalIdentities().get(0), null);
        final IssueTokens issueFlow = new IssueTokens(
                Collections.singletonList(usdTokenBob),
                Collections.emptyList());
        final CordaFuture<SignedTransaction> issueFuture = usMint.startFlow(issueFlow);
        network.runNetwork();
        issueFuture.get();

        // Pass the expiration.
        ((TestClock) notary.getServices().getClock()).advanceBy(Duration.ofSeconds(11));

        // Buyer accepts.
        final AcceptSimpleFlow acceptFlow = new AcceptSimpleFlow(proposal.getState().getData().getLinearId());
        final CordaFuture<SignedTransaction> acceptFuture = bob.startFlow(acceptFlow);
        network.runNetwork();
        try {
            acceptFuture.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
}
