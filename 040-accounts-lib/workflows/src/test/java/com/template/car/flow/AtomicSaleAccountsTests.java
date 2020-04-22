package com.template.car.flow;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo;
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
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AtomicSaleAccountsTests {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;
    private final TokenType usdTokenType;
    private final IssuedTokenType usMintUsd;

    public AtomicSaleAccountsTests() {
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
        if (accountInfo == null) throw new NullPointerException("You have to start it from a host that knows");
        if (!host.getInfo().getLegalIdentities().get(0).equals(accountInfo.getState().getData().getHost())) {
            throw new IllegalArgumentException("hosts do not match");
        }
        for (StartedMockNode other : others) {
            final CordaFuture<?> future = host.startFlow(new SyncKeyMappingInitiator(
                    other.getInfo().getLegalIdentities().get(0),
                    Collections.singletonList(new AnonymousParty(who))));
            network.runNetwork();
            future.get();
        }
        final CordaFuture<?> future = host.startFlow(new ShareAccountInfo(accountInfo, others.stream()
                .map(it -> it.getInfo().getLegalIdentities().get(0))
                .collect(Collectors.toList())));
        network.runNetwork();
        future.get();
    }


    @NotNull
    private SignedTransaction createNewBmw(
            @SuppressWarnings("SameParameterValue") @NotNull final String vin,
            @SuppressWarnings("SameParameterValue") @NotNull final String make,
            @SuppressWarnings("SameParameterValue") final long price,
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
        // We keep our account-safe IssueCarToHolderFlow instead of creating an account-aware one.
        final IssueCarToHolderFlow flow = new IssueCarToHolderFlow(
                car, bmwDealer.getInfo().getLegalIdentities().get(0), holder);
        final CordaFuture<SignedTransaction> future = bmwDealer.startFlow(flow);
        network.runNetwork();
        return future.get();
    }

    @Test
    public void accountsCanDoAtomicSaleAccounts() throws Exception {
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
        final UUID emmaId = emma.getState().getData().getIdentifier().getId();
        // This emma key will not be used when holding the car token.
        final AnonymousParty emmaParty = requestNewKey(bob, emma.getState().getData());
        // Inform the seller's host and the mint about who is emma.
        inform(bob, emmaParty.getOwningKey(), Arrays.asList(alice, usMint));
        // Issue dollars to Bob (to make sure we pay only with Emma's dollars) and Emma.
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
        final AtomicSaleAccounts.CarSeller saleFlow = new AtomicSaleAccounts.CarSeller(
                dansBmwPointer, emmaId, usMintUsd);
        final CordaFuture<SignedTransaction> saleFuture = alice.startFlow(saleFlow);
        network.runNetwork();
        final SignedTransaction saleTx = saleFuture.get();

        final AccountService bobAccountService = bob.getServices()
                .cordaService(KeyManagementBackedAccountService.class);

        // Emma got the car
        final List<NonFungibleToken> emmaCarTokens = saleTx.getCoreTransaction().outputsOfType(NonFungibleToken.class);
        assertEquals(1, emmaCarTokens.size());
        final NonFungibleToken emmaCarToken = emmaCarTokens.get(0);
        assertEquals(emmaId, bobAccountService.accountIdForKey(emmaCarToken.getHolder().getOwningKey()));
        // Emma got the car on a new public key.
        assertNotEquals(emmaParty.getOwningKey(), emmaCarToken.getHolder().getOwningKey());
        //noinspection unchecked
        final UniqueIdentifier emmaCarType = ((TokenPointer<CarTokenType>) emmaCarToken.getTokenType())
                .getPointer().getPointer();
        assertEquals(bmw.getLinearId(), emmaCarType);

        // Dan got the money.
        final long paidToDan = saleTx.getCoreTransaction().outputsOfType(FungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(danParty))
                .filter(it -> it.getIssuedTokenType().equals(usMintUsd))
                .map(it -> it.getAmount().getQuantity())
                .reduce(0L, Math::addExact);
        assertEquals(AmountUtilitiesKt.amount(25_000L, usdTokenType).getQuantity(), paidToDan);

        // Emma got the change on her new public key.
        final long paidToEmma = saleTx.getCoreTransaction().outputsOfType(FungibleToken.class)
                .stream()
                .filter(it -> it.getHolder().equals(emmaCarToken.getHolder()))
                .filter(it -> it.getIssuedTokenType().equals(usMintUsd))
                .map(it -> it.getAmount().getQuantity())
                .reduce(0L, Math::addExact);
        assertEquals(AmountUtilitiesKt.amount(5_000L, usdTokenType).getQuantity(), paidToEmma);
    }

}
