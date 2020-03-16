package com.template.flows;

import com.template.states.TokenState;
import javafx.util.Pair;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.testing.node.StartedMockNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.junit.Assert.assertEquals;

class FlowHelpers {

    static @NotNull
    TokenState createFrom(
            @NotNull final StartedMockNode issuer,
            @NotNull final StartedMockNode holder,
            long quantity) {
        return new TokenState(
                issuer.getInfo().getLegalIdentities().get(0),
                holder.getInfo().getLegalIdentities().get(0),
                quantity);
    }

    static @NotNull
    Pair<Party, Long> toPair(@NotNull final TokenState token) {
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

}
