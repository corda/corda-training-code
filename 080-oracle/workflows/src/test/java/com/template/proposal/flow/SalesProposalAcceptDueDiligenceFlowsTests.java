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
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.car.flow.CarTokenCourseHelpers;
import com.template.car.flow.CarTokenTypeConstants;
import com.template.car.flow.IssueCarToHolderFlows.IssueCarToHolderFlow;
import com.template.car.flow.IssueCarTokenTypeFlows.IssueCarTokenTypeFlow;
import com.template.car.flow.UpdateCarTokenTypeFlows.UpdateCarTokenTypeFlow;
import com.template.car.flow.UsdTokenConstants;
import com.template.car.state.CarTokenType;
import com.template.diligence.flow.DiligenceOracle;
import com.template.diligence.flow.DiligenceOracleInternalFlows;
import com.template.diligence.flow.DueDiligenceOracleFlows.Prepare;
import com.template.diligence.state.DiligenceOracleUtilities;
import com.template.diligence.state.DueDiligence;
import com.template.proposal.flow.SalesProposalAcceptFlows.AcceptSimpleFlow;
import com.template.proposal.flow.SalesProposalOfferFlows.OfferSimpleFlow;
import com.template.proposal.state.SalesProposal;
import net.corda.core.CordaRuntimeException;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNodeParameters;
import net.corda.testing.node.StartedMockNode;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SalesProposalAcceptDueDiligenceFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    private final IssuedTokenType usMintUsd;
    private final Party notaryParty;
    private final Party usMintParty;
    private final Party dmvParty;
    private final Party dealerParty;
    private final Party aliceParty;
    private final Party bobParty;
    private final AbstractParty oracleParty;

    public SalesProposalAcceptDueDiligenceFlowsTests() throws Exception {
        network = new MockNetwork(CarTokenCourseHelpers.prepareMockNetworkParameters());
        notary = network.getDefaultNotaryNode();
        notaryParty = notary.getInfo().getLegalIdentities().get(0);
        usMint = network.createNode(new MockNodeParameters()
                .withLegalName(UsdTokenConstants.US_MINT));
        usMintParty = usMint.getInfo().getLegalIdentities().get(0);
        dmv = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenTypeConstants.DMV));
        dmvParty = dmv.getInfo().getLegalIdentities().get(0);
        bmwDealer = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenTypeConstants.BMW_DEALER));
        dealerParty = bmwDealer.getInfo().getLegalIdentities().get(0);
        alice = network.createNode(new MockNodeParameters()
                .withLegalName(CordaX500Name.parse("O=Alice, L=Istanbul, C=TR")));
        aliceParty = alice.getInfo().getLegalIdentities().get(0);
        bob = network.createNode(new MockNodeParameters()
                .withLegalName(CordaX500Name.parse("O=Bob, L=Paris, C=FR")));
        bobParty = bob.getInfo().getLegalIdentities().get(0);
        final TokenType usdTokenType = FiatCurrency.Companion.getInstance("USD");
        usMintUsd = new IssuedTokenType(usMint.getInfo().getLegalIdentities().get(0), usdTokenType);
        oracleParty = requestNewKey(dmv, createAccount(dmv, DiligenceOracle.ACCOUNT_NAME).getState().getData());
        informKeys(dmv, Collections.singletonList(oracleParty.getOwningKey()),
                Arrays.asList(bmwDealer, alice, bob));
        setOracleKey(oracleParty.getOwningKey());
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

    private void setOracleKey(@NotNull final PublicKey oracleKey) throws Exception {
        final DiligenceOracleInternalFlows.SetOracleKeyFlow flow = new DiligenceOracleInternalFlows.SetOracleKeyFlow(oracleKey);
        final CordaFuture<Void> future = dmv.startFlow(flow);
        network.runNetwork();
        future.get();
    }

    private void setStatus(
            @NotNull final UniqueIdentifier tokenId,
            @NotNull final DiligenceOracleUtilities.Status status) throws Exception {
        final DiligenceOracleInternalFlows.SetStatus flow = new DiligenceOracleInternalFlows.SetStatus(tokenId, status);
        final CordaFuture<Void> future = dmv.startFlow(flow);
        network.runNetwork();
        future.get();
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
        final IssueCarToHolderFlow flow = new IssueCarToHolderFlow(
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

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    private SignedTransaction issueDollars(
            @NotNull final AbstractParty holder,
            final long amount) throws Exception {
        final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(amount, usMintUsd);
        final FungibleToken usdTokens = new FungibleToken(amountOfUsd,
                holder, null);
        final IssueTokens issueFlow = new IssueTokens(
                Collections.singletonList(usdTokens),
                Collections.emptyList());
        final CordaFuture<SignedTransaction> issueFuture = usMint.startFlow(issueFlow);
        network.runNetwork();
        return issueFuture.get();
    }

    @SuppressWarnings("UnusedReturnValue")
    @NotNull
    private SignedTransaction issueDollars(
            @NotNull final StartedMockNode owner,
            final long amount) throws Exception {
        return issueDollars(owner.getInfo().getLegalIdentities().get(0), amount);
    }

    @Test
    public void buyerCanAcceptSalesProposalWithDueDiligence() throws Throwable {
        // Seller is on alice.
        final StateAndRef<AccountInfo> seller = createAccount(alice, "carly");
        final AnonymousParty sellerParty = requestNewKey(alice, seller.getState().getData());
        informKeys(alice, Collections.singletonList(sellerParty.getOwningKey()), Collections.singletonList(bmwDealer));
        // Buyer is on bob.
        final StateAndRef<AccountInfo> buyer = createAccount(bob, "dan");
        final AnonymousParty buyerParty = requestNewKey(bob, buyer.getState().getData());
        informKeys(bob, Collections.singletonList(buyerParty.getOwningKey()), Arrays.asList(alice, usMint));
        // The car.
        final StateAndRef<CarTokenType> bmwType = createNewBmw("abc124", "BMW",
                Collections.singletonList(dealerParty))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final StateAndRef<NonFungibleToken> bmw1 = issueCarTo(
                bmwType.getState().getData().toPointer(CarTokenType.class),
                sellerParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0);
        // Seller makes an offer.
        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMintParty, 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final StateAndRef<SalesProposal> proposal = offerFuture.get().getTx().outRef(0);
        // Issue dollars to Buyer.
        issueDollars(buyerParty, 20_000L);
        // Dmv changes the car with informing the buyer only.
        updateMileageOn(bmwType, 8_000L, 22_000L,
                Collections.singletonList(bobParty));

        // Buyer creates a DueDiligence
        final Prepare.PrepareFlow flow = new Prepare.PrepareFlow(
                Collections.singletonList(buyerParty), bmw1.getState().getData().getLinearId(),
                notaryParty, oracleParty);
        final CordaFuture<StateAndRef<DueDiligence>> prepareFuture = bob.startFlow(flow);
        network.runNetwork();
        final StateAndRef<DueDiligence> dueDil = prepareFuture.get();

        // Oracle says clear.
        setStatus(bmw1.getState().getData().getLinearId(), DiligenceOracleUtilities.Status.Clear);

        // Buyer accepts.
        final AcceptSimpleFlow acceptFlow = new AcceptSimpleFlow(
                proposal.getState().getData().getLinearId(),
                dueDil.getState().getData().getLinearId(),
                DiligenceOracleUtilities.Status.Clear
        );
        final CordaFuture<SignedTransaction> acceptFuture = bob.startFlow(acceptFlow);
        network.runNetwork();
        final SignedTransaction acceptTx = acceptFuture.get();

        // The oracle signed.
        assertEquals(oracleParty.getOwningKey(), acceptTx.getSigs().get(3).getBy());

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

    @Test(expected = CordaRuntimeException.class)
    public void buyerCannotAcceptLinkedSalesProposalWithDueDiligenceExpectingClear() throws Throwable {
        // Seller is on alice.
        final StateAndRef<AccountInfo> seller = createAccount(alice, "carly");
        final AnonymousParty sellerParty = requestNewKey(alice, seller.getState().getData());
        informKeys(alice, Collections.singletonList(sellerParty.getOwningKey()), Collections.singletonList(bmwDealer));
        // Buyer is on bob.
        final StateAndRef<AccountInfo> buyer = createAccount(bob, "dan");
        final AnonymousParty buyerParty = requestNewKey(bob, buyer.getState().getData());
        informKeys(bob, Collections.singletonList(buyerParty.getOwningKey()), Arrays.asList(alice, usMint));
        // The car.
        final StateAndRef<CarTokenType> bmwType = createNewBmw("abc124", "BMW",
                Collections.singletonList(dealerParty))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final StateAndRef<NonFungibleToken> bmw1 = issueCarTo(
                bmwType.getState().getData().toPointer(CarTokenType.class),
                sellerParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0);
        // Seller makes an offer.
        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMintParty, 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final StateAndRef<SalesProposal> proposal = offerFuture.get().getTx().outRef(0);
        // Issue dollars to Buyer.
        issueDollars(buyerParty, 20_000L);
        // Dmv changes the car with informing the buyer only.
        updateMileageOn(bmwType, 8_000L, 22_000L,
                Collections.singletonList(bobParty));

        // Buyer creates a DueDiligence
        final Prepare.PrepareFlow flow = new Prepare.PrepareFlow(
                Collections.singletonList(buyerParty), bmw1.getState().getData().getLinearId(),
                notaryParty, oracleParty);
        final CordaFuture<StateAndRef<DueDiligence>> prepareFuture = bob.startFlow(flow);
        network.runNetwork();
        final StateAndRef<DueDiligence> dueDil = prepareFuture.get();

        // Oracle has lien.
        setStatus(bmw1.getState().getData().getLinearId(), DiligenceOracleUtilities.Status.Linked);

        // Buyer accepts.
        final AcceptSimpleFlow acceptFlow = new AcceptSimpleFlow(
                proposal.getState().getData().getLinearId(),
                dueDil.getState().getData().getLinearId(),
                DiligenceOracleUtilities.Status.Clear
        );
        final CordaFuture<SignedTransaction> acceptFuture = bob.startFlow(acceptFlow);
        network.runNetwork();
        try {
            acceptFuture.get();
        } catch (ExecutionException e) {
            throw e.getCause().getCause();
        }
    }

    @Test(expected = FlowException.class)
    public void buyerCannotAcceptSalesProposalWithDueDiligenceForWrongToken() throws Throwable {
        // Seller is on alice.
        final StateAndRef<AccountInfo> seller = createAccount(alice, "carly");
        final AnonymousParty sellerParty = requestNewKey(alice, seller.getState().getData());
        informKeys(alice, Collections.singletonList(sellerParty.getOwningKey()), Collections.singletonList(bmwDealer));
        // Buyer is on bob.
        final StateAndRef<AccountInfo> buyer = createAccount(bob, "dan");
        final AnonymousParty buyerParty = requestNewKey(bob, buyer.getState().getData());
        informKeys(bob, Collections.singletonList(buyerParty.getOwningKey()), Arrays.asList(alice, usMint));
        // The car.
        final StateAndRef<CarTokenType> bmwType = createNewBmw("abc124", "BMW",
                Collections.singletonList(dealerParty))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final StateAndRef<NonFungibleToken> bmw1 = issueCarTo(
                bmwType.getState().getData().toPointer(CarTokenType.class),
                sellerParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0);
        // Seller makes an offer.
        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMintParty, 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final StateAndRef<SalesProposal> proposal = offerFuture.get().getTx().outRef(0);
        // Issue dollars to Buyer.
        issueDollars(buyerParty, 20_000L);
        // Dmv changes the car with informing the buyer only.
        updateMileageOn(bmwType, 8_000L, 22_000L,
                Collections.singletonList(bobParty));

        // Buyer creates a DueDiligence
        final Prepare.PrepareFlow flow = new Prepare.PrepareFlow(
                Collections.singletonList(buyerParty), new UniqueIdentifier(),
                notaryParty, oracleParty);
        final CordaFuture<StateAndRef<DueDiligence>> prepareFuture = bob.startFlow(flow);
        network.runNetwork();
        final StateAndRef<DueDiligence> dueDil = prepareFuture.get();

        // Oracle has lien.
        setStatus(bmw1.getState().getData().getLinearId(), DiligenceOracleUtilities.Status.Linked);

        // Buyer accepts.
        final AcceptSimpleFlow acceptFlow = new AcceptSimpleFlow(
                proposal.getState().getData().getLinearId(),
                dueDil.getState().getData().getLinearId(),
                DiligenceOracleUtilities.Status.Clear
        );
        final CordaFuture<SignedTransaction> acceptFuture = bob.startFlow(acceptFlow);
        network.runNetwork();
        try {
            acceptFuture.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

}
