package com.template.account;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

public class AccountCourseExercise {
    private final MockNetwork network;
    private final StartedMockNode alice;
    private final StartedMockNode bob;

    public AccountCourseExercise() {
        network = new MockNetwork(new MockNetworkParameters()
                .withCordappsForAllNodes(ImmutableList.of(
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"))));
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

    @Test
    public void canCreateAccount() throws Exception {
        final StateAndRef<AccountInfo> carol = createAccount(alice, "carol");

        assertEquals(alice.getInfo().getLegalIdentities().get(0), carol.getState().getData().getHost());
        assertEquals("carol", carol.getState().getData().getName());
    }

    @Test
    public void canRequestANewKeyAppearsAsAlice() throws Exception {
        final StateAndRef<AccountInfo> dan = createAccount(alice, "dan");
        final AnonymousParty danParty = requestNewKey(alice, dan.getState().getData());
        final Party whoIsDan = alice.getServices().getIdentityService().wellKnownPartyFromAnonymous(danParty);

        assertEquals(alice.getInfo().getLegalIdentities().get(0), whoIsDan);
    }

    @Test
    public void requestKeyAgainIsDifferent() throws Exception {
        final StateAndRef<AccountInfo> dan = createAccount(alice, "dan");
        final AnonymousParty danParty = requestNewKey(alice, dan.getState().getData());
        final AnonymousParty danParty2 = requestNewKey(alice, dan.getState().getData());

        assertNotEquals(danParty, danParty2);
    }

    @Test
    public void newKeyIsUnknownToStranger() throws Exception {
        final StateAndRef<AccountInfo> dan = createAccount(alice, "dan");
        final AnonymousParty danParty = requestNewKey(alice, dan.getState().getData());

        assertNull(bob.getServices().getIdentityService().wellKnownPartyFromAnonymous(danParty));
        assertNull(bob.getServices().getIdentityService().partyFromKey(danParty.getOwningKey()));
    }

    @Test
    public void canRequestANewKeyFromNonHost() throws Exception {
        final StateAndRef<AccountInfo> dan = createAccount(alice, "dan");
        final AnonymousParty danParty = requestNewKey(bob, dan.getState().getData());
        final Party whoIsDanByAlice = alice.getServices().getIdentityService().wellKnownPartyFromAnonymous(danParty);
        final Party whoIsDanKeyByAlice = alice.getServices().getIdentityService().partyFromKey(danParty.getOwningKey());
        final Party whoIsDanByBob = bob.getServices().getIdentityService().wellKnownPartyFromAnonymous(danParty);
        final Party whoIsDanKeyByBob = bob.getServices().getIdentityService().partyFromKey(danParty.getOwningKey());


        assertEquals(alice.getInfo().getLegalIdentities().get(0), whoIsDanByAlice);
        assertEquals(alice.getInfo().getLegalIdentities().get(0), whoIsDanKeyByAlice);
        assertEquals(alice.getInfo().getLegalIdentities().get(0), whoIsDanByBob);
        assertEquals(alice.getInfo().getLegalIdentities().get(0), whoIsDanKeyByBob);
    }

    @Test//(expected = IllegalStateException.class)
    public void canRequestNewKeyFromNonAccount() throws Throwable {
        final AccountInfo fake = new AccountInfo(
                "dan",
                alice.getInfo().getLegalIdentities().get(0),
                new UniqueIdentifier());
        try {
            requestNewKey(alice, fake);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void cannotRequestNewKeyFromNonAccountFromAfar() throws Throwable {
        final AccountInfo fake = new AccountInfo(
                "dan",
                alice.getInfo().getLegalIdentities().get(0),
                new UniqueIdentifier());
        try {
            requestNewKey(bob, fake);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void accountWithX500IsStillNode() throws Exception {
        final StateAndRef<AccountInfo> carol = createAccount(alice, "O=Carol, L=Berlin, C=DE");
        final AnonymousParty carolParty = requestNewKey(alice, carol.getState().getData());
        final Party whoIsCarolByAlice = alice.getServices().getIdentityService().partyFromKey(carolParty.getOwningKey());
        assertEquals(alice.getInfo().getLegalIdentities().get(0), whoIsCarolByAlice);
    }

    @Test
    public void accountFromAccountServiceIsCorrect() throws Exception {
        final StateAndRef<AccountInfo> carol = createAccount(alice, "O=Carol, L=Berlin, C=DE");
        final AnonymousParty carolParty = requestNewKey(alice, carol.getState().getData());
        final AccountService aliceAccountService = alice.getServices()
                .cordaService(KeyManagementBackedAccountService.class);
        final UUID accountFound = aliceAccountService.accountIdForKey(carolParty.getOwningKey());
        final StateAndRef<AccountInfo> carolAgain = aliceAccountService.accountInfo(carolParty.getOwningKey());
        //noinspection ConstantConditions
        final List<PublicKey> carolKeys = aliceAccountService.accountKeys(accountFound);

        assertEquals(carol.getState().getData().getLinearId().getId(), accountFound);
        assertEquals(carol, carolAgain);
        assertEquals(Collections.singletonList(carolParty.getOwningKey()), carolKeys);
    }

}
