package com.template.flows;

import com.template.states.TokenState;
import javafx.util.Pair;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

class FlowHelpers {

    @NotNull
    static TokenState createFrom(
            @NotNull final StartedMockNode issuer,
            @NotNull final StartedMockNode holder,
            final long quantity) {
        return new TokenState(
                issuer.getInfo().getLegalIdentities().get(0),
                holder.getInfo().getLegalIdentities().get(0),
                quantity);
    }

    @NotNull
    static Pair<Party, Long> toPair(@NotNull final TokenState token) {
        return new Pair<>(token.getHolder(), token.getQuantity());
    }

    static void assertHasStatesInVault(
            @NotNull final StartedMockNode node,
            @NotNull final List<TokenState> tokenStates) {
        final List<StateAndRef<TokenState>> vaultTokens = node.transaction(() ->
                node.getServices().getVaultService().queryBy(TokenState.class).getStates());
        assertEquals(tokenStates.size(), vaultTokens.size());
        for (int i = 0; i < tokenStates.size(); i++) {
            // The equals and hashcode functions are implemented correctly.
            assertEquals(vaultTokens.get(i).getState().getData(), tokenStates.get(i));
        }
    }

    static class NodeHolding {
        @NotNull
        public final StartedMockNode holder;
        public final long quantity;

        public NodeHolding(@NotNull final StartedMockNode holder, final long quantity) {
            this.holder = holder;
            this.quantity = quantity;
        }

        @NotNull
        public Pair<Party, Long> toPair() {
            return new Pair<>(holder.getInfo().getLegalIdentities().get(0), quantity);
        }
    }

    @NotNull
    static List<StateAndRef<TokenState>> issueTokens(
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
                .outRefsOfType(TokenState.class);
    }

}
