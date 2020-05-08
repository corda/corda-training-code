package com.template.diligence.flow;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.template.car.flow.*;
import com.template.car.flow.IssueCarToHolderFlows.IssueCarToHolderFlow;
import com.template.car.flow.IssueCarTokenTypeFlows.IssueCarTokenTypeFlow;
import com.template.car.state.CarTokenType;
import com.template.diligence.flow.DiligenceOracleInternalFlows.SetStatus;
import com.template.diligence.state.DiligenceOracleUtilities.Status;
import com.template.diligence.state.DueDiligence;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
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
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class DiligenceOracleInternalFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    private final IssuedTokenType usMintDollars;
    private final Party notaryParty;
    private final Party dmvParty;
    private final Party dealerParty;
    private final Party aliceParty;
    private final Party bobParty;
    private final AbstractParty oracleParty;

    public DiligenceOracleInternalFlowsTests() throws Exception {
        network = new MockNetwork(CarTokenCourseHelpers.prepareMockNetworkParameters());
        notary = network.getDefaultNotaryNode();
        notaryParty = notary.getInfo().getLegalIdentities().get(0);
        usMint = network.createNode(new MockNodeParameters()
                .withLegalName(UsdTokenConstants.US_MINT));
        dmv = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenTypeConstants.DMV));
        dmvParty = dmv.getInfo().getLegalIdentities().get(0);
        bmwDealer = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenTypeConstants.BMW_DEALER));
        dealerParty = bmwDealer.getInfo().getLegalIdentities().get(0);
        alice = network.createNode();
        aliceParty = alice.getInfo().getLegalIdentities().get(0);
        bob = network.createNode();
        bobParty = bob.getInfo().getLegalIdentities().get(0);
        usMintDollars = new IssuedTokenType(
                usMint.getInfo().getLegalIdentities().get(0),
                FiatCurrency.Companion.getInstance("USD"));
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
            @NotNull final Status status) throws Exception {
        final SetStatus flow = new SetStatus(tokenId, status);
        final CordaFuture<Void> future = dmv.startFlow(flow);
        network.runNetwork();
        future.get();
    }

    @NotNull
    private StateAndRef<CarTokenType> createNewBmw(
            @SuppressWarnings("SameParameterValue") @NotNull final String vin,
            @SuppressWarnings("SameParameterValue") @NotNull final String make,
            @NotNull final List<Party> observers) throws Exception {
        final IssueCarTokenTypeFlow flow = new IssueCarTokenTypeFlow(notaryParty,
                vin, make, observers);
        final CordaFuture<SignedTransaction> future = dmv.startFlow(flow);
        network.runNetwork();
        return future.get().getCoreTransaction().outRef(0);
    }

    @NotNull
    private StateAndRef<NonFungibleToken> issueCarTo(
            @NotNull final TokenPointer<CarTokenType> car,
            @NotNull final AbstractParty holder) throws Exception {
        return issueCarTo(car, holder, Collections.emptyList());
    }

    @NotNull
    private StateAndRef<NonFungibleToken> issueCarTo(
            @NotNull final TokenPointer<CarTokenType> car,
            @NotNull final AbstractParty holder,
            @NotNull final List<Party> observers) throws Exception {
        final IssueCarToHolderFlow flow = new IssueCarToHolderFlow(
                car, dealerParty, holder, observers);
        final CordaFuture<SignedTransaction> future = bmwDealer.startFlow(flow);
        network.runNetwork();
        return future.get().getCoreTransaction().outRef(0);
    }

    @Test
    public void canQueryOracleForClear() throws Exception {
        network.runNetwork();
        final UniqueIdentifier tokenId = new UniqueIdentifier();
        // Set clear status
        setStatus(tokenId, Status.Clear);

        final DueDiligenceOracleFlows.Query.Request flow = new DueDiligenceOracleFlows.Query.Request(oracleParty, tokenId);
        final CordaFuture<Status> offerFuture = alice.startFlow(flow);
        network.runNetwork();
        final Status status = offerFuture.get();

        assertEquals(Status.Clear, status);
    }

    @Test
    public void canQueryOracleForLinked() throws Exception {
        network.runNetwork();
        final UniqueIdentifier tokenId = new UniqueIdentifier();
        // Set clear status
        setStatus(tokenId, Status.Linked);

        final DueDiligenceOracleFlows.Query.Request flow = new DueDiligenceOracleFlows.Query.Request(oracleParty, tokenId);
        final CordaFuture<Status> offerFuture = alice.startFlow(flow);
        network.runNetwork();
        final Status status = offerFuture.get();

        assertEquals(Status.Linked, status);
    }

    @Test
    public void canSignIfLinkedCorrect() throws Exception {
        final StateAndRef<CarTokenType> bmwType = createNewBmw("abc123", "Bmw",
                Collections.singletonList(dealerParty));
        final StateAndRef<NonFungibleToken> bmw = issueCarTo(bmwType.getState().getData().toPointer(CarTokenType.class),
                bobParty);
        final UniqueIdentifier tokenId = bmw.getState().getData().getLinearId();
        final DueDiligenceOracleFlows.Prepare.PrepareFlow prepareFlow = new DueDiligenceOracleFlows.Prepare.PrepareFlow(
                Arrays.asList(aliceParty, bobParty), tokenId, notaryParty, oracleParty);
        final CordaFuture<StateAndRef<DueDiligence>> prepareFuture = alice.startFlow(prepareFlow);
        network.runNetwork();
        final StateAndRef<DueDiligence> dueDilRef = prepareFuture.get();

        network.runNetwork();
        setStatus(tokenId, Status.Linked);

        final DueDiligenceOracleFlows.Certify.RequestStraight signFlow = new DueDiligenceOracleFlows.Certify.RequestStraight(dueDilRef, Status.Linked);
        final CordaFuture<SignedTransaction> signFuture = alice.startFlow(signFlow);
        network.runNetwork();
        final SignedTransaction signTx = signFuture.get();

        assertEquals(1, signTx.getInputs().size());
        assertEquals(dueDilRef.getRef(), signTx.getInputs().get(0));
        assertEquals(2, signTx.getSigs().size());
        assertEquals(oracleParty.getOwningKey(), signTx.getSigs().get(0).getBy());
        assertEquals(notaryParty.getOwningKey(), signTx.getSigs().get(1).getBy());
    }

}
