package com.template.flows;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.template.states.AirMileType;
import javafx.util.Pair;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

interface FlowHelpers {

    @NotNull
    static MockNetworkParameters prepareMockNetworkParameters() throws Exception {
        Properties tokenProperties = new Properties();
        tokenProperties.load(new FileReader(new File("res/tokens-workflows.conf")));
        Map<String, String> tokensConfig = new HashMap<>();
        for (Map.Entry<Object, Object> next : tokenProperties.entrySet()) {
            final String rawValue = (String) next.getValue();
            final String value;
            if ((rawValue.startsWith("\"") && rawValue.endsWith("\"")) ||
                    (rawValue.startsWith("'") && rawValue.endsWith("'"))) {
                value = rawValue.substring(1, rawValue.length() - 1);
            } else {
                value = rawValue;
            }
            tokensConfig.put((String) next.getKey(), value);
        }
        return new MockNetworkParameters()
                .withNotarySpecs(ImmutableList.of(
                        new MockNetworkNotarySpec(CordaX500Name.parse("O=Unwanted Notary, L=London, C=GB")),
                        new MockNetworkNotarySpec(CordaX500Name.parse(tokensConfig.get("notary")))))
                .withCordappsForAllNodes(ImmutableList.of(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.template.flows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
                                .withConfig(tokensConfig)));
    }

    @NotNull
    static FungibleToken createFrom(
            @NotNull final StartedMockNode issuer,
            @NotNull final StartedMockNode holder,
            final long quantity) {
        return new FungibleToken(
                AmountUtilitiesKt.amount(
                        quantity,
                        new IssuedTokenType(issuer.getInfo().getLegalIdentities().get(0), AirMileType.create())),
                holder.getInfo().getLegalIdentities().get(0),
                null);
    }

    @NotNull
    static Pair<AbstractParty, Long> toPair(@NotNull final FungibleToken token) {
        return new Pair<>(token.getHolder(), token.getAmount().getQuantity());
    }

    static void assertHasStatesInVault(
            @NotNull final StartedMockNode node,
            @NotNull final List<FungibleToken> tokenStates) {
        final List<StateAndRef<FungibleToken>> vaultTokens = node.transaction(() ->
                node.getServices().getVaultService().queryBy(FungibleToken.class).getStates());
        assertEquals(tokenStates.size(), vaultTokens.size());
        for (int i = 0; i < tokenStates.size(); i++) {
            // The equals and hashcode functions are implemented correctly.
            assertEquals(vaultTokens.get(i).getState().getData(), tokenStates.get(i));
        }
    }

    class NodeHolding {
        @NotNull
        public final StartedMockNode holder;
        public final long quantity;

        public NodeHolding(@NotNull final StartedMockNode holder, final long quantity) {
            this.holder = holder;
            this.quantity = quantity;
        }

        @NotNull
        public Pair<AbstractParty, Long> toPair() {
            return new Pair<>(holder.getInfo().getLegalIdentities().get(0), quantity);
        }
    }

    @NotNull
    static List<StateAndRef<FungibleToken>> issueTokens(
            @NotNull final StartedMockNode node,
            @NotNull final MockNetwork network,
            @NotNull final Collection<NodeHolding> nodeHoldings)
            throws Throwable {
        final IssueFlows.Initiator flow = new IssueFlows.Initiator(nodeHoldings.stream()
                .map(NodeHolding::toPair)
                .collect(Collectors.toList()));
        final CordaFuture<SignedTransaction> future = node.startFlow(flow);
        network.runNetwork();
        final SignedTransaction tx = future.get();
        return tx.toLedgerTransaction(node.getServices())
                .outRefsOfType(FungibleToken.class);
    }

}
