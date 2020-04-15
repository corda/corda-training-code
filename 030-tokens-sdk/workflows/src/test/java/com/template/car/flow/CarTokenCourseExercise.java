package com.template.car.flow;

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.template.car.*;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNodeParameters;
import net.corda.testing.node.StartedMockNode;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.template.car.flow.CarTokenCourseHelpers.prepareMockNetworkParameters;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CarTokenCourseExercise {

    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;

    public CarTokenCourseExercise() throws Exception {
        network = new MockNetwork(prepareMockNetworkParameters());
        notary = network.getDefaultNotaryNode();
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
    private SignedTransaction createNewBmw(
            @NotNull final String vin,
            @SuppressWarnings("SameParameterValue") @NotNull final String make,
            final long price,
            @NotNull final List<Party> observers) throws Exception {
        final IssueCarTokenTypeFlow flow = new IssueCarTokenTypeFlow(notary.getInfo().getLegalIdentities().get(0),
                vin, make, price, observers);
        final CordaFuture<SignedTransaction> future = dmv.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @NotNull
    private SignedTransaction issueCarTo(
            @NotNull final CarTokenType car,
            @NotNull final AbstractParty holder) throws Exception {
        final IssueCarToHolderFlow flow = new IssueCarToHolderFlow(
                car, bmwDealer.getInfo().getLegalIdentities().get(0), holder);
        final CordaFuture<SignedTransaction> future = bmwDealer.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @NotNull
    private SignedTransaction updateMileageOn(
            @NotNull final StateAndRef<CarTokenType> carRef,
            final long mileage,
            final long price,
            @NotNull final List<Party> observers) throws Exception {
        final UpdateCarTokenTypeFlow flow = new UpdateCarTokenTypeFlow(carRef, mileage, price, observers);
        final CordaFuture<SignedTransaction> future = dmv.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @NotNull
    private SignedTransaction moveCarTo(
            @NotNull final TokenPointer<CarTokenType> carTokenTypePointer,
            @NotNull final AbstractParty newHolder,
            @NotNull final List<Party> observers) throws Exception {
        final MoveCarToNewHolderFlow flow = new MoveCarToNewHolderFlow(carTokenTypePointer,
                newHolder,
                observers);
        final CordaFuture<SignedTransaction> future = alice.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @Test
    public void isCreated() throws Exception {
        final CarTokenType bmw = createNewBmw("abc123", "BMW", 30_000L, Collections.emptyList())
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0).getState().getData();
        assertEquals("abc123", bmw.getVin());
        assertEquals("BMW", bmw.getMake());
        assertEquals(0L, bmw.getMileage());
        assertEquals(30_000L, bmw.getPrice());
    }

    @Test
    public void isIssuedToAlice() throws Exception {
        final CarTokenType bmw = createNewBmw("abc124", "BMW", 25_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0).getState().getData();
        final NonFungibleToken alicesBmw = issueCarTo(bmw, alice.getInfo().getLegalIdentities().get(0))
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();
        assertEquals(alice.getInfo().getLegalIdentities().get(0), alicesBmw.getHolder());
        assertEquals(bmwDealer.getInfo().getLegalIdentities().get(0), alicesBmw.getIssuedTokenType().getIssuer());
        assertTrue(alicesBmw.getIssuedTokenType().getTokenType().isPointer());
        //noinspection rawtypes
        final TokenPointer pointer = (TokenPointer) alicesBmw.getIssuedTokenType().getTokenType();
        assertEquals(CarTokenType.class, pointer.getPointer().getType());
        final CarTokenType bmwRetrievedFromDealer = (CarTokenType) pointer.getPointer().resolve(bmwDealer.getServices())
                .getState().getData();
        assertEquals(bmw, bmwRetrievedFromDealer);
        final CarTokenType bmwRetrievedFromAlice = (CarTokenType) pointer.getPointer().resolve(alice.getServices())
                .getState().getData();
        assertEquals(bmw, bmwRetrievedFromAlice);
    }

    @Test
    public void isIssuedThenUpdated() throws Exception {
        final StateAndRef<CarTokenType> bmwRef = createNewBmw("abc125", "BMW", 30_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final CarTokenType bmw = bmwRef.getState().getData();
        issueCarTo(bmw, alice.getInfo().getLegalIdentities().get(0))
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();
        // The DMV forgets to inform Alice BTW.
        final CarTokenType updatedBmw = updateMileageOn(bmwRef, 8_000L, 22_000L, Collections.emptyList())
                .getCoreTransaction().outputsOfType(CarTokenType.class).get(0);
        assertEquals(bmw.getLinearId(), updatedBmw.getLinearId());
        assertEquals("abc125", updatedBmw.getVin());
        assertEquals("BMW", updatedBmw.getMake());
        assertEquals(8_000L, updatedBmw.getMileage());
        assertEquals(22_000L, updatedBmw.getPrice());

        // Alice still has the old version because it was not an observer.
        final List<StateAndRef<CarTokenType>> aliceCarTypes = alice.getServices().getVaultService()
                .queryBy(CarTokenType.class).getStates();
        assertEquals(1, aliceCarTypes.size());
        final CarTokenType bmwAccordingToAlice = aliceCarTypes.get(0).getState().getData();
        assertEquals(0L, bmwAccordingToAlice.getMileage());
        assertEquals(30_000L, bmwAccordingToAlice.getPrice());
    }

    @Test
    public void isIssuedUpdatedAndSoldToBob() throws Exception {
        final StateAndRef<CarTokenType> bmwRef = createNewBmw("abc126", "BMW", 30_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final CarTokenType bmw = bmwRef.getState().getData();
        final NonFungibleToken alicesBmw = issueCarTo(bmw, alice.getInfo().getLegalIdentities().get(0))
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();
        final CarTokenType updatedBmw = updateMileageOn(
                bmwRef, 9_000L, 21_000L,
                // Now, Alice needs to know.
                Collections.singletonList(alice.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outputsOfType(CarTokenType.class).get(0);
        final NonFungibleToken bobsBmw = moveCarTo(
                updatedBmw.toPointer(CarTokenType.class),
                bob.getInfo().getLegalIdentities().get(0),
                Collections.emptyList())
                .getCoreTransaction().outputsOfType(NonFungibleToken.class).get(0);
        assertEquals(alicesBmw.getLinearId(), bobsBmw.getLinearId());
        assertEquals(bob.getInfo().getLegalIdentities().get(0), bobsBmw.getHolder());

        // Bob has the new BMW version because Alice was an observer.
        final List<StateAndRef<CarTokenType>> carTypes = bob.getServices().getVaultService()
                .queryBy(CarTokenType.class).getStates();
        assertEquals(1, carTypes.size());
        final CarTokenType bmwAccordingToBob = carTypes.get(0).getState().getData();
        assertEquals(9_000L, bmwAccordingToBob.getMileage());
        assertEquals(21_000L, bmwAccordingToBob.getPrice());
    }

}

