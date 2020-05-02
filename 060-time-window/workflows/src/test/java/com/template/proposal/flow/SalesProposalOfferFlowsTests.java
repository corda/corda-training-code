package com.template.proposal.flow;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.template.car.flow.CarTokenCourseHelpers;
import com.template.car.flow.CarTokenTypeConstants;
import com.template.car.flow.IssueCarToHolderFlows;
import com.template.car.flow.IssueCarTokenTypeFlows.IssueCarTokenTypeFlow;
import com.template.car.flow.UpdateCarTokenTypeFlows.UpdateCarTokenTypeFlow;
import com.template.car.flow.UsdTokenConstants;
import com.template.car.state.CarTokenType;
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

public class SalesProposalOfferFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;

    public SalesProposalOfferFlowsTests() {
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

    @SuppressWarnings("SameParameterValue")
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
    public void accountCanDoSalesProposalAndInformOtherAccount() throws Exception {
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

        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMint.getInfo().getLegalIdentities().get(0), 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final SignedTransaction offerTx = offerFuture.get();

        // Bob got the transaction.
        final SignedTransaction savedTx = bob.getServices().getValidatedTransactions().getTransaction(offerTx.getId());
        //noinspection ConstantConditions
        assertEquals(2, savedTx.getReferences().size());
        assertEquals(bmwType.getRef(), savedTx.getReferences().get(0));
        assertEquals(bmw1.getRef(), savedTx.getReferences().get(1));
        assertTrue(savedTx.getInputs().isEmpty());
        assertEquals(1, savedTx.getCoreTransaction().getOutputs().size());
        final SalesProposal proposal = (SalesProposal) savedTx.getTx().outRef(0).getState().getData();
        assertEquals(sellerParty, proposal.getSeller());
        assertEquals(buyerParty, proposal.getBuyer());
        final Amount<IssuedTokenType> expectedPrice = AmountUtilitiesKt.amount(
                11_000L, new IssuedTokenType(
                        usMint.getInfo().getLegalIdentities().get(0),
                        FiatCurrency.Companion.getInstance("USD")));
        assertEquals(expectedPrice, proposal.getPrice());
        assertEquals(bmw1, proposal.getAsset());

        // Bob can find the proposal by linear id.
        final List<StateAndRef<SalesProposal>> foundProposals = bob.getServices().getVaultService().queryBy(
                SalesProposal.class,
                new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(proposal.getLinearId().getId())))
                .getStates();
        assertEquals(1, foundProposals.size());
        assertEquals(proposal, foundProposals.get(0).getState().getData());

        // Bob can find the car type.
        final List<StateAndRef<CarTokenType>> foundBmwTypes = bob.getServices().getVaultService().queryBy(
                CarTokenType.class,
                new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(bmwType.getState().getData().getLinearId().getId())))
                .getStates();
        assertEquals(1, foundBmwTypes.size());
        assertEquals("abc124", foundBmwTypes.get(0).getState().getData().getVin());

        // Bob can find the held car.
        final List<StateAndRef<NonFungibleToken>> foundBmws = bob.getServices().getVaultService().queryBy(
                NonFungibleToken.class,
                new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(bmw1.getState().getData().getLinearId().getId())))
                .getStates();
        assertEquals(1, foundBmws.size());
        assertEquals(sellerParty, foundBmws.get(0).getState().getData().getHolder());
    }

    // This test demonstrates that alice needs to have both the TokenType and the NFToken up to date to attach the
    // NFToken as a reference data.
    @Test(expected = NotaryException.class)
    public void accountCannotDoSalesProposalIfMileageHasChanged() throws Throwable {
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
        // Dmv changes the car without informing the seller or alice.
        updateMileageOn(bmwType, 8_000L, 22_000L, Collections.emptyList());

        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMint.getInfo().getLegalIdentities().get(0), 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        try {
            offerFuture.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void accountCantDoSalesProposalIfMileageHasChangedAndWasInformed() throws Throwable {
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
        // Dmv changes the car without informing the seller or alice.
        final StateAndRef<CarTokenType> updatedBmwType = updateMileageOn(bmwType, 8_000L,
                22_000L, Arrays.asList(
                        bmwDealer.getInfo().getLegalIdentities().get(0),
                        alice.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction()
                .outRef(0);

        final OfferSimpleFlow offerFlow = new OfferSimpleFlow(
                bmw1.getState().getData().getLinearId(), buyerParty, 11_000L, "USD",
                usMint.getInfo().getLegalIdentities().get(0), 3600);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.runNetwork();
        final SignedTransaction offerTx = offerFuture.get();

        // Bob got the transaction.
        final SignedTransaction savedTx = bob.getServices().getValidatedTransactions().getTransaction(offerTx.getId());
        //noinspection ConstantConditions
        assertEquals(2, savedTx.getReferences().size());
        // The updated type went in the transaction.
        assertEquals(updatedBmwType.getRef(), savedTx.getReferences().get(0));
        assertEquals(bmw1.getRef(), savedTx.getReferences().get(1));
        assertEquals(1, savedTx.getCoreTransaction().getOutputs().size());
        final SalesProposal proposal = (SalesProposal) savedTx.getTx().outRef(0).getState().getData();
        assertEquals(bmw1, proposal.getAsset());

        // Bob can find the proposal by linear id.
        final List<StateAndRef<SalesProposal>> foundProposals = bob.getServices().getVaultService().queryBy(
                SalesProposal.class,
                new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(proposal.getLinearId().getId())))
                .getStates();
        assertEquals(1, foundProposals.size());
        assertEquals(proposal, foundProposals.get(0).getState().getData());

        // Bob can find the updated car type.
        final List<StateAndRef<CarTokenType>> foundBmwTypes = bob.getServices().getVaultService().queryBy(
                CarTokenType.class,
                new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(bmwType.getState().getData().getLinearId().getId())))
                .getStates();
        assertEquals(1, foundBmwTypes.size());
        assertEquals(8_000L, foundBmwTypes.get(0).getState().getData().getMileage());

        // Bob can find the held car.
        final List<StateAndRef<NonFungibleToken>> foundBmws = bob.getServices().getVaultService().queryBy(
                NonFungibleToken.class,
                new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(bmw1.getState().getData().getLinearId().getId())))
                .getStates();
        assertEquals(1, foundBmws.size());
        assertEquals(sellerParty, foundBmws.get(0).getState().getData().getHolder());
    }

}
