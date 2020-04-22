package com.template.account;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.ShareStateAndSyncAccounts;
import com.template.dummy.flow.DummyFlow;
import com.template.dummy.state.DummyState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AccountVaultCourseExercise {
    private final MockNetwork network;
    private final StartedMockNode alice;
    private final StartedMockNode bob;

    public AccountVaultCourseExercise() {
        network = new MockNetwork(new MockNetworkParameters()
                .withCordappsForAllNodes(ImmutableList.of(
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows"),
                        TestCordapp.findCordapp("com.template.dummy.state"),
                        TestCordapp.findCordapp("com.template.dummy.flow"))));
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
    public void canFetchStateOfSeenWhenKnowsAccount() throws Exception {
        final AccountInfo lost = createAccount(alice, "lost").getState().getData();
        final AnonymousParty lostParty = requestNewKey(alice, lost);
        final AccountInfo seen = createAccount(alice, "seen").getState().getData();
        final AnonymousParty seenParty = requestNewKey(alice, seen);
        final CordaFuture<SignedTransaction> future = alice.startFlow(new DummyFlow.Create(
                Collections.singletonList(new DummyState(lostParty, seenParty))));
        network.runNetwork();
        future.get();

        final QueryCriteria participatingAccountCriteria = new QueryCriteria.VaultQueryCriteria()
                .withExternalIds(Collections.singletonList(seen.getIdentifier().getId()));
        final List<StateAndRef<DummyState>> results = alice.getServices().getVaultService()
                .queryBy(DummyState.class, participatingAccountCriteria).getStates();

        assertEquals(1, results.size());
        assertEquals(new DummyState(lostParty, seenParty), results.get(0).getState().getData());
    }

    @Test
    public void observerCanFetchStateOfSeenOnlyWhenKnowsAboutAccount() throws Exception {
        final AccountInfo lost = createAccount(alice, "lost").getState().getData();
        final AnonymousParty lostParty = requestNewKey(alice, lost);
        final StateAndRef<AccountInfo> seen = createAccount(alice, "seen");
        final AnonymousParty seenParty = requestNewKey(alice, seen.getState().getData());
        final CordaFuture<SignedTransaction> future = alice.startFlow(new DummyFlow.Create(
                Collections.singletonList(new DummyState(lostParty, seenParty)),
                // Send public keys and state to Bob
                Collections.singletonList(bob.getInfo().getLegalIdentities().get(0))));
        network.runNetwork();
        final SignedTransaction createTx = future.get();

        // Bob recorded the state
        final List<StateAndRef<DummyState>> results0 = bob.getServices().getVaultService()
                .queryBy(DummyState.class).getStates();
        assertEquals(1, results0.size());
        assertEquals(new DummyState(lostParty, seenParty), results0.get(0).getState().getData());

        // But Bob cannot associate it to the account's id
        final QueryCriteria participatingAccountCriteria = new QueryCriteria.VaultQueryCriteria()
                .withExternalIds(Collections.singletonList(seen.getState().getData().getIdentifier().getId()));
        final List<StateAndRef<DummyState>> results1 = bob.getServices().getVaultService()
                .queryBy(DummyState.class, participatingAccountCriteria).getStates();

        assertEquals(0, results1.size());

        // Now we inform Bob about the account only.
        //noinspection rawtypes
        final CordaFuture infoFuture = alice.startFlow(new ShareAccountInfo(seen,
                Collections.singletonList(bob.getInfo().getLegalIdentities().get(0))));
        network.runNetwork();
        infoFuture.get();

        final List<StateAndRef<DummyState>> results2 = bob.getServices().getVaultService()
                .queryBy(DummyState.class, participatingAccountCriteria).getStates();

        assertEquals(0, results2.size());

        // Now we inform Bob about the account properly, with keys too.
        //noinspection rawtypes
        final CordaFuture infoFuture2 = alice.startFlow(new ShareStateAndSyncAccounts(
                createTx.getCoreTransaction().outRefsOfType(DummyState.class).get(0),
                bob.getInfo().getLegalIdentities().get(0)));
        network.runNetwork();
        infoFuture2.get();

        final List<StateAndRef<DummyState>> results3 = bob.getServices().getVaultService()
                .queryBy(DummyState.class, participatingAccountCriteria).getStates();

        // TODO why is it actually 0?
        assertEquals(1, results3.size());
        assertEquals(new DummyState(lostParty, seenParty), results3.get(0).getState().getData());
    }

    @Test
    public void cannotFetchStateOfLostWhenKnowsAccount() throws Exception {
        final AccountInfo lost = createAccount(alice, "lost").getState().getData();
        final AnonymousParty lostParty = requestNewKey(alice, lost);
        final AccountInfo seen = createAccount(alice, "seen").getState().getData();
        final AnonymousParty seenParty = requestNewKey(alice, seen);
        final CordaFuture<SignedTransaction> future = alice.startFlow(new DummyFlow.Create(
                Collections.singletonList(new DummyState(lostParty, seenParty))));
        network.runNetwork();
        future.get();

        final QueryCriteria participatingAccountCriteria = new QueryCriteria.VaultQueryCriteria()
                .withExternalIds(Collections.singletonList(lost.getIdentifier().getId()));
        final List<StateAndRef<DummyState>> results = alice.getServices().getVaultService()
                .queryBy(DummyState.class, participatingAccountCriteria).getStates();

        assertEquals(0, results.size());
    }

}
