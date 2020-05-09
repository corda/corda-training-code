package com.example.flow;

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.car.flow.CarTokenCourseHelpers;
import com.template.car.flow.CarTokenTypeConstants;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNodeParameters;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class LinearStateFlowsTests {
    private final MockNetwork network;
    private final StartedMockNode bmwDealer;
    private final StartedMockNode alice;
    private final StartedMockNode bob;

    public LinearStateFlowsTests() {
        network = new MockNetwork(CarTokenCourseHelpers.prepareMockNetworkParameters());
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
    public void recordOtherNFTWithSameUuid() throws Throwable {
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
