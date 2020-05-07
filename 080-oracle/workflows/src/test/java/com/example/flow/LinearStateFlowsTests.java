package com.example.flow;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.car.flow.CarTokenCourseHelpers;
import com.template.car.flow.CarTokenTypeConstants;
import com.template.car.flow.IssueCarTokenTypeFlows.IssueCarTokenTypeFlow;
import com.template.car.flow.UpdateCarTokenTypeFlows.UpdateCarTokenTypeFlow;
import com.template.car.flow.UsdTokenConstants;
import com.template.car.state.CarTokenType;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.CordaX500Name;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class LinearStateFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode notary;
    private final StartedMockNode usMint;
    private final StartedMockNode dmv;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;

    public LinearStateFlowsTests() {
        network = new MockNetwork(CarTokenCourseHelpers.prepareMockNetworkParameters());
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
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Test
    public void failsToRecordOtherNFTWithSameUuid() throws Throwable {
        // Issue NFT to Alice.
        final UniqueIdentifier id = new UniqueIdentifier();
        final NonFungibleToken nft = new NonFungibleToken(
                new IssuedTokenType(bmwDealer.getInfo().getLegalIdentities().get(0),
                        FiatCurrency.Companion.getInstance("USD")),
                alice.getInfo().getLegalIdentities().get(0), id, null);
        // Issue new NFT to Alice with same UUID
        for (int i = 0; i < 10; i++) {
            final CordaFuture<SignedTransaction> future = bmwDealer.startFlow(
                    new IssueTokens(Collections.singletonList(nft),
                            Collections.singletonList(bob.getInfo().getLegalIdentities().get(0))));
            network.runNetwork();
            future.get();
        }

        // Alice has 10 NFTs of same id
        final QueryCriteria criteria = new QueryCriteria.LinearStateQueryCriteria()
                .withUuid(Collections.singletonList(id.getId()));
        assertEquals(10, alice.getServices().getVaultService().queryBy(NonFungibleToken.class, criteria)
                .getStates().size());

        // Bob has 10 NFTs of same id
        assertEquals(10, bob.getServices().getVaultService().queryBy(NonFungibleToken.class, criteria)
                .getStates().size());
    }

}
