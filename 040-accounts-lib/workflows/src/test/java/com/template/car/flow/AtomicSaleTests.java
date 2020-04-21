package com.template.car.flow;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken;
import com.template.car.state.CarTokenType;
import com.template.usd.UsdTokenConstants;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNodeParameters;
import net.corda.testing.node.StartedMockNode;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class AtomicSaleTests {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    @SuppressWarnings("FieldCanBeLocal")
    private final TokenType usdTokenType;
    private final IssuedTokenType usMintUsd;

    public AtomicSaleTests() {
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
        usdTokenType = FiatCurrency.Companion.getInstance("USD");
        usMintUsd = new IssuedTokenType(usMint.getInfo().getLegalIdentities().get(0), usdTokenType);
    }

    @Before
    public void setup() throws Exception {
        network.runNetwork();
        // Issue dollars to Bob
        final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(50_000L, usMintUsd);
        final FungibleToken usdToken = new FungibleToken(amountOfUsd,
                bob.getInfo().getLegalIdentities().get(0), null);
        final IssueTokens flow = new IssueTokens(
                Collections.singletonList(usdToken),
                Collections.emptyList());
        final CordaFuture<SignedTransaction> future = usMint.startFlow(flow);
        network.runNetwork();
        future.get();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @NotNull
    private NonFungibleToken issueUpdatedCarToAlice(
            @SuppressWarnings("SameParameterValue")
            @NotNull final String vin,
            @SuppressWarnings("SameParameterValue") @NotNull final String make,
            @SuppressWarnings("SameParameterValue") final long price,
            @SuppressWarnings("SameParameterValue") final long nextPrice,
            @SuppressWarnings("SameParameterValue") final long nextMileage) throws Exception {
        // At DMV
        final CarTokenType newCar = new CarTokenType(Collections.singletonList(dmv.getInfo().getLegalIdentities().get(0)),
                new UniqueIdentifier(), vin, make, 0, price);
        final TransactionState<CarTokenType> txState = new TransactionState<>(newCar,
                notary.getInfo().getLegalIdentities().get(0));
        final CreateEvolvableTokens carTokenFlow = new CreateEvolvableTokens(txState,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)));
        final CordaFuture<SignedTransaction> carTokenFuture = dmv.startFlow(carTokenFlow);
        network.runNetwork();
        final StateAndRef<CarTokenType> bmwRef = carTokenFuture.get()
                .getCoreTransaction()
                .outRefsOfType(CarTokenType.class)
                .get(0);
        final CarTokenType bmwType = bmwRef.getState().getData();
        // At dealership
        final TokenPointer<CarTokenType> bmwPointer = bmwType.toPointer(CarTokenType.class);
        final IssuedTokenType bmwWithDealership = new IssuedTokenType(bmwDealer.getInfo().getLegalIdentities().get(0),
                bmwPointer);
        final NonFungibleToken heldCar = new NonFungibleToken(
                bmwWithDealership, alice.getInfo().getLegalIdentities().get(0),
                new UniqueIdentifier(), null);
        final IssueTokens issueFlow = new IssueTokens(Collections.singletonList(heldCar));
        final CordaFuture<SignedTransaction> issueFuture = bmwDealer.startFlow(issueFlow);
        network.runNetwork();
        final NonFungibleToken alicesBmw = issueFuture.get()
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();
        // At DMV
        final CarTokenType updatedCar = new CarTokenType(bmwType.getMaintainers(), bmwType.getLinearId(),
                bmwType.getVin(), bmwType.getMake(), nextMileage, nextPrice);
        final UpdateEvolvableToken updateFlow = new UpdateEvolvableToken(bmwRef, updatedCar,
                // We have to add them as observers because as of now, the DMV was not contacted when issuing to Alice.
                // See https://github.com/corda/token-sdk/issues/197
                Arrays.asList(alice.getInfo().getLegalIdentities().get(0),
                        bmwDealer.getInfo().getLegalIdentities().get(0)));
        final CordaFuture<SignedTransaction> updateFuture = dmv.startFlow(updateFlow);
        network.runNetwork();
        updateFuture.get();
        return alicesBmw;
    }

    @Test
    public void saleIsAsExpected() throws Exception {
        final NonFungibleToken bmw = issueUpdatedCarToAlice("abc123", "BMW", 30_000L,
                21_000L, 9_000L);
        //noinspection unchecked
        final TokenPointer<CarTokenType> bmwPointer = (TokenPointer<CarTokenType>) bmw.getTokenType();
        final AtomicSale.CarSeller saleFlow = new AtomicSale.CarSeller(bmwPointer, bob.getInfo().getLegalIdentities().get(0),
                usMintUsd);
        final CordaFuture<SignedTransaction> saleFuture = alice.startFlow(saleFlow);
        network.runNetwork();
        final SignedTransaction saleTx = saleFuture.get();

        // Alice got paid
        final List<FungibleToken> aliceUsdTokens = saleTx.getCoreTransaction().outputsOfType(FungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(alice.getInfo().getLegalIdentities().get(0)))
                .filter(it -> it.getIssuedTokenType().equals(usMintUsd))
                .collect(Collectors.toList());
        final Amount<IssuedTokenType> aliceReceived = Amount.sumOrThrow(aliceUsdTokens.stream()
                .map(FungibleToken::getAmount)
                .collect(Collectors.toList()));
        assertEquals(
                AmountUtilitiesKt.amount(21_000L, usdTokenType).getQuantity(),
                aliceReceived.getQuantity());

        // Bob got the car
        final List<NonFungibleToken> bobCarTokens = saleTx.getCoreTransaction().outputsOfType(NonFungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(bob.getInfo().getLegalIdentities().get(0)))
                .collect(Collectors.toList());
        assertEquals(1, bobCarTokens.size());
        final NonFungibleToken bobCarToken = bobCarTokens.get(0);
        //noinspection unchecked
        final UniqueIdentifier bobCarType = ((TokenPointer<CarTokenType>) bobCarToken.getTokenType())
                .getPointer().getPointer();
        assertEquals(bmwPointer.getPointer().getPointer(), bobCarType);
    }

    @Test
    public void saleAccountIsAsExpected() throws Exception {
        final NonFungibleToken bmw = issueUpdatedCarToAlice("abc123", "BMW", 30_000L,
                21_000L, 9_000L);
        //noinspection unchecked
        final TokenPointer<CarTokenType> bmwPointer = (TokenPointer<CarTokenType>) bmw.getTokenType();
        final AtomicSaleAccounts.CarSeller saleFlow = new AtomicSaleAccounts.CarSeller(bmwPointer,
                bob.getInfo().getLegalIdentities().get(0),
                usMintUsd);
        final CordaFuture<SignedTransaction> saleFuture = alice.startFlow(saleFlow);
        network.runNetwork();
        final SignedTransaction saleTx = saleFuture.get();

        // Alice got paid
        final List<FungibleToken> aliceUsdTokens = saleTx.getCoreTransaction().outputsOfType(FungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(alice.getInfo().getLegalIdentities().get(0)))
                .filter(it -> it.getIssuedTokenType().equals(usMintUsd))
                .collect(Collectors.toList());
        final Amount<IssuedTokenType> aliceReceived = Amount.sumOrThrow(aliceUsdTokens.stream()
                .map(FungibleToken::getAmount)
                .collect(Collectors.toList()));
        assertEquals(
                AmountUtilitiesKt.amount(21_000L, usdTokenType).getQuantity(),
                aliceReceived.getQuantity());

        // Bob got the car
        final List<NonFungibleToken> bobCarTokens = saleTx.getCoreTransaction().outputsOfType(NonFungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(bob.getInfo().getLegalIdentities().get(0)))
                .collect(Collectors.toList());
        assertEquals(1, bobCarTokens.size());
        final NonFungibleToken bobCarToken = bobCarTokens.get(0);
        //noinspection unchecked
        final UniqueIdentifier bobCarType = ((TokenPointer<CarTokenType>) bobCarToken.getTokenType())
                .getPointer().getPointer();
        assertEquals(bmwPointer.getPointer().getPointer(), bobCarType);
    }

}
