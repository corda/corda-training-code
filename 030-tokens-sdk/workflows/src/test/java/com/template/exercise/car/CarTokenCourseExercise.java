package com.template.exercise.car;

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveNonFungibleTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken;
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.AbstractParty;
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

import java.util.Collections;
import java.util.List;

import static com.template.exercise.car.CarTokenCourseHelpers.prepareMockNetworkParameters;
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
                .withLegalName(CarTokenCourseHelpers.DMV));
        bmwDealer = network.createNode(new MockNodeParameters()
                .withLegalName(CarTokenCourseHelpers.BMW_DEALER));
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
        final CarTokenType car = carRef.getState().getData();
        final CarTokenType updatedCar = new CarTokenType(car.getMaintainers(), car.getLinearId(),
                car.getVin(), car.getMake(), mileage, price);
        final UpdateEvolvableToken flow = new UpdateEvolvableToken(carRef, updatedCar, observers);
        final CordaFuture<SignedTransaction> future = dmv.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @NotNull
    private SignedTransaction moveCarTo(
            @NotNull final TokenPointer<CarTokenType> carTokenTypePointer,
            @NotNull final AbstractParty newHolder) throws Exception {
        final PartyAndToken bobAndBmw = new PartyAndToken(newHolder, carTokenTypePointer);
        final QueryCriteria alicesBmwCriteria = QueryUtilitiesKt.heldTokenAmountCriteria(
                carTokenTypePointer, alice.getInfo().getLegalIdentities().get(0));
        final MoveNonFungibleTokens flow = new MoveNonFungibleTokens(bobAndBmw, Collections.emptyList());
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
    public void isUpdated() throws Exception {
        final StateAndRef<CarTokenType> bmwRef = createNewBmw("abc125", "BMW", 30_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final CarTokenType bmw = bmwRef.getState().getData();
        final NonFungibleToken alicesBmw = issueCarTo(bmw, alice.getInfo().getLegalIdentities().get(0))
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();
        final CarTokenType updatedBmw = updateMileageOn(bmwRef, 8_000L, 22_000L, Collections.emptyList())
                .getCoreTransaction().outputsOfType(CarTokenType.class).get(0);
        assertEquals(bmw.getLinearId(), updatedBmw.getLinearId());
        assertEquals("abc125", updatedBmw.getVin());
        assertEquals("BMW", updatedBmw.getMake());
        assertEquals(8_000L, updatedBmw.getMileage());
        assertEquals(22_000L, updatedBmw.getPrice());
    }

    @Test
    public void isSoldToBob() throws Exception {
        final StateAndRef<CarTokenType> bmwRef = createNewBmw("abc126", "BMW", 30_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0);
        final CarTokenType bmw = bmwRef.getState().getData();
        final NonFungibleToken alicesBmw = issueCarTo(bmw, alice.getInfo().getLegalIdentities().get(0))
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();

        final CarTokenType updatedBmw = updateMileageOn(
                bmwRef, 9_000L, 21_000L,
                // Alice needs to know now.
                Collections.singletonList(alice.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outputsOfType(CarTokenType.class).get(0);
        // noinspection unchecked,rawtypes,rawtypes
        final NonFungibleToken bobsBmw = moveCarTo(
                (TokenPointer) alicesBmw.getIssuedTokenType().getTokenType(),
                bob.getInfo().getLegalIdentities().get(0))
                .getCoreTransaction().outputsOfType(NonFungibleToken.class).get(0);
        assertEquals(alicesBmw.getLinearId(), bobsBmw.getLinearId());
        assertEquals(bob.getInfo().getLegalIdentities().get(0), bobsBmw.getHolder());
        assertEquals(bmw.getLinearId(), updatedBmw.getLinearId());
        assertEquals("abc126", updatedBmw.getVin());
        assertEquals("BMW", updatedBmw.getMake());
        assertEquals(9_000L, updatedBmw.getMileage());
        assertEquals(21_000L, updatedBmw.getPrice());
    }

}

