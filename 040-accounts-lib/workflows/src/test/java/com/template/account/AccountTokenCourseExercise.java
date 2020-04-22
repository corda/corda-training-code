package com.template.account;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.car.flow.*;
import com.template.car.state.CarTokenType;
import com.template.usd.UsdTokenConstants;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Amount;
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
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.template.account.AccountTokenCourseHelpers.prepareMockNetworkParameters;
import static org.junit.Assert.assertEquals;

public class AccountTokenCourseExercise {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    private final TokenType usdTokenType;
    private final IssuedTokenType usMintUsd;

    public AccountTokenCourseExercise() {
        network = new MockNetwork(prepareMockNetworkParameters());
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

    private void inform(
            @NotNull final StartedMockNode host,
            @NotNull final PublicKey who,
            @NotNull final List<StartedMockNode> others) throws Exception {
        final AccountService accountService = host.getServices()
                .cordaService(KeyManagementBackedAccountService.class);
        final StateAndRef<AccountInfo> accountInfo = accountService.accountInfo(who);
        //noinspection ConstantConditions
        if (!host.getInfo().getLegalIdentities().get(0).equals(accountInfo.getState().getData().getHost())) {
            throw new IllegalArgumentException("hosts do not match");
        }
        // TODO how to not use `CordaFuture<Unit>`?
        for (StartedMockNode other : others) {
            //noinspection rawtypes
            final CordaFuture future = host.startFlow(new SyncKeyMappingInitiator(
                    other.getInfo().getLegalIdentities().get(0),
                    Collections.singletonList(new AnonymousParty(who))));
            network.runNetwork();
            future.get();
        }
    }

    @NotNull
    private SignedTransaction createNewBmw(
            @NotNull final String vin,
            @NotNull final String make,
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

    @Test(expected = IllegalArgumentException.class)
    public void accountNeedsToBeKnownToHoldCar() throws Throwable {
        final CarTokenType bmw = createNewBmw("abc124", "BMW", 25_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0).getState().getData();
        final StateAndRef<AccountInfo> dan = createAccount(alice, "dan");
        final AnonymousParty danParty = requestNewKey(alice, dan.getState().getData());
        try {
            issueCarTo(bmw, danParty)
                    .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                    .getState().getData();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void accountDanCanHoldCar() throws Exception {
        final CarTokenType bmw = createNewBmw("abc124", "BMW", 25_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0).getState().getData();
        final StateAndRef<AccountInfo> dan = createAccount(alice, "dan");
        final AnonymousParty danParty = requestNewKey(alice, dan.getState().getData());
        // Inform the dealer about who is dan.
        inform(alice, danParty.getOwningKey(), Collections.singletonList(bmwDealer));
        final NonFungibleToken dansBmw = issueCarTo(bmw, danParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();

        //noinspection unchecked
        assertEquals(bmw.getLinearId(), ((TokenPointer<CarTokenType>) dansBmw.getTokenType()).getPointer().getPointer());
    }

    @Test
    public void accountDanCanGiveCarAwayToEmma() throws Exception {
        final CarTokenType bmw = createNewBmw("abc124", "BMW", 25_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0).getState().getData();
        final StateAndRef<AccountInfo> dan = createAccount(alice, "dan");
        final AnonymousParty danParty = requestNewKey(alice, dan.getState().getData());
        // Inform the dealer about who is dan.
        inform(alice, danParty.getOwningKey(), Collections.singletonList(bmwDealer));
        final NonFungibleToken dansBmw = issueCarTo(bmw, danParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();
        final StateAndRef<AccountInfo> emma = createAccount(bob, "emma");
        final AnonymousParty emmaParty = requestNewKey(bob, emma.getState().getData());
        // Inform the seller about who is dan.
        inform(bob, emmaParty.getOwningKey(), Collections.singletonList(alice));
        final NonFungibleToken emmasBmw = moveCarTo(
                bmw.toPointer(CarTokenType.class),
                emmaParty,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outputsOfType(NonFungibleToken.class).get(0);

        // Emma has the BMW because Alice was an observer.
        assertEquals(emmasBmw.getHolder(), emmaParty);
    }

    @Test
    public void accountsCanDoAtomicSale() throws Exception {
        final CarTokenType bmw = createNewBmw("abc124", "BMW", 25_000L,
                Collections.singletonList(bmwDealer.getInfo().getLegalIdentities().get(0)))
                .getCoreTransaction().outRefsOfType(CarTokenType.class).get(0).getState().getData();
        final StateAndRef<AccountInfo> dan = createAccount(alice, "dan");
        final AnonymousParty danParty = requestNewKey(alice, dan.getState().getData());
        // Inform the dealer and the mint about who is dan.
        inform(alice, danParty.getOwningKey(), Arrays.asList(bmwDealer, usMint));
        final NonFungibleToken dansBmw = issueCarTo(bmw, danParty)
                .getCoreTransaction().outRefsOfType(NonFungibleToken.class).get(0)
                .getState().getData();
        final StateAndRef<AccountInfo> emma = createAccount(bob, "emma");
        final AnonymousParty emmaParty = requestNewKey(bob, emma.getState().getData());
        // Inform the seller and the mint about who is dan.
        inform(bob, emmaParty.getOwningKey(), Arrays.asList(alice, usMint));
        // Issue dollars to Bob and Emma
        final Amount<IssuedTokenType> amountOfUsd = AmountUtilitiesKt.amount(30_000L, usMintUsd);
        final FungibleToken usdTokenBob = new FungibleToken(amountOfUsd,
                bob.getInfo().getLegalIdentities().get(0), null);
        final FungibleToken usdTokenEmma = new FungibleToken(amountOfUsd,
                emmaParty, null);
        final IssueTokens flow = new IssueTokens(
                Arrays.asList(usdTokenBob, usdTokenEmma),
                Collections.emptyList());
        final CordaFuture<SignedTransaction> future = usMint.startFlow(flow);
        network.runNetwork();
        future.get();
        // Proceed with the sale
        //noinspection unchecked
        final TokenPointer<CarTokenType> dansBmwPointer = (TokenPointer<CarTokenType>) dansBmw.getTokenType();
        final AtomicSaleAccountsSafe.CarSeller saleFlow = new AtomicSaleAccountsSafe.CarSeller(
                dansBmwPointer, emmaParty, usMintUsd);
        final CordaFuture<SignedTransaction> saleFuture = alice.startFlow(saleFlow);
        network.runNetwork();
        final SignedTransaction saleTx = saleFuture.get();

        // Emma got the car
        final List<NonFungibleToken> emmaCarTokens = saleTx.getCoreTransaction().outputsOfType(NonFungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(emmaParty))
                .collect(Collectors.toList());
        assertEquals(1, emmaCarTokens.size());
        final NonFungibleToken emmaCarToken = emmaCarTokens.get(0);
        //noinspection unchecked
        final UniqueIdentifier emmaCarType = ((TokenPointer<CarTokenType>) emmaCarToken.getTokenType())
                .getPointer().getPointer();
        assertEquals(bmw.getLinearId(), emmaCarType);

        // Dan got the money
        final long paidToDan = saleTx.getCoreTransaction().outputsOfType(FungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(danParty))
                .filter(it -> it.getIssuedTokenType().equals(usMintUsd))
                .map(it -> it.getAmount().getQuantity())
                .reduce(0L, Math::addExact);
        assertEquals(AmountUtilitiesKt.amount(25_000L, usdTokenType).getQuantity(), paidToDan);

        // Emma got the change
        final long paidToEmma = saleTx.getCoreTransaction().outputsOfType(FungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(emmaParty))
                .filter(it -> it.getIssuedTokenType().equals(usMintUsd))
                .map(it -> it.getAmount().getQuantity())
                .reduce(0L, Math::addExact);
        assertEquals(AmountUtilitiesKt.amount(5_000L, usdTokenType).getQuantity(), paidToEmma);
    }

}
