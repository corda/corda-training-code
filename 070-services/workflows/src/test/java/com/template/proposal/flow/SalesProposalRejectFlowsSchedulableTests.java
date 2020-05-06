package com.template.proposal.flow;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.template.car.flow.CarTokenCourseHelpers;
import com.template.car.flow.CarTokenTypeConstants;
import com.template.car.flow.IssueCarToHolderFlows;
import com.template.car.flow.IssueCarTokenTypeFlows.IssueCarTokenTypeFlow;
import com.template.car.flow.UsdTokenConstants;
import com.template.car.state.CarTokenType;
import com.template.proposal.flow.SalesProposalOfferFlows.OfferSimpleFlow;
import com.template.proposal.flow.SalesProposalRejectFlows.RejectSimpleFlow;
import com.template.proposal.state.SalesProposal;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.CordaX500Name;
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
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SalesProposalRejectFlowsSchedulableTests {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;

    public SalesProposalRejectFlowsSchedulableTests() {
        network = new MockNetwork(CarTokenCourseHelpers.prepareMockNetworkParameters()
                // To accommodate the schedulable events.
                .withThreadPerNode(true));
        notary = network.getDefaultNotaryNode();
        usMint = network.createNode(new MockNodeParameters()
                .withLegalName(UsdTokenConstants.US_MINT));
        dmv = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenTypeConstants.DMV));
        bmwDealer = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenTypeConstants.BMW_DEALER));
        alice = network.createNode(new MockNodeParameters()
                .withLegalName(CordaX500Name.parse("O=Alice, L=Istanbul, C=TR")));
        bob = network.createNode(new MockNodeParameters()
                .withLegalName(CordaX500Name.parse("O=Bob, L=Paris, C=FR")));
    }

    @Before
    public void setup() {
        network.waitQuiescent();
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
        network.waitQuiescent();
        //noinspection unchecked
        return (StateAndRef<AccountInfo>) future.get();
    }

    @NotNull
    private AnonymousParty requestNewKey(
            @NotNull final StartedMockNode host,
            @NotNull final AccountInfo forWhom) throws Exception {
        final CordaFuture<AnonymousParty> future = host.startFlow(new RequestKeyForAccount(forWhom));
        network.waitQuiescent();
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
            network.waitQuiescent();
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
        network.waitQuiescent();
        return future.get();
    }

    @NotNull
    private SignedTransaction issueCarTo(
            @NotNull final TokenPointer<CarTokenType> car,
            @NotNull final AbstractParty holder) throws Exception {
        final IssueCarToHolderFlows.IssueCarToHolderFlow flow = new IssueCarToHolderFlows.IssueCarToHolderFlow(
                car, bmwDealer.getInfo().getLegalIdentities().get(0), holder);
        final CordaFuture<SignedTransaction> future = bmwDealer.startFlow(flow);
        network.waitQuiescent();
        return future.get();
    }

    @Test
    public void salesProposalSchedulableFlowNameIsCorrect() {
        assertEquals(
                RejectSimpleFlow.class.getName(),
                SalesProposal.SCHEDULED_FLOW);
    }

    @Test
    public void sellerAccountAutomaticallyRejectsSalesProposalAfterExpiration() throws Exception {
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
                usMint.getInfo().getLegalIdentities().get(0), 100);
        final CordaFuture<SignedTransaction> offerFuture = alice.startFlow(offerFlow);
        network.waitQuiescent();
        final StateAndRef<SalesProposal> proposal = offerFuture.get().getTx().outRef(0);

        // Advance a lot to be sure the network has figured it out.
        ((TestClock) notary.getServices().getClock()).advanceBy(Duration.ofSeconds(2500));
        ((TestClock) alice.getServices().getClock()).advanceBy(Duration.ofSeconds(2500));
        network.waitQuiescent(); // Pump the network.
        // Seller rejects automatically with the scheduled activity.

        // Bob cannot find the proposal by linear id.
        final List<StateAndRef<SalesProposal>> foundProposals = bob.getServices().getVaultService().queryBy(
                SalesProposal.class,
                new QueryCriteria.LinearStateQueryCriteria()
                        .withUuid(Collections.singletonList(proposal.getState().getData().getLinearId().getId())))
                .getStates();
        assertTrue(foundProposals.isEmpty());
    }
}
