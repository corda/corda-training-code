package com.template.states;

import com.google.common.collect.ImmutableMap;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public interface TokenStateUtilities {

    /**
     * @param states The states to tally.
     * @return The mapped sums of token quantities per issuer.
     */
    static Map<Party, Long> mapSumByIssuer(@NotNull final Collection<TokenState> states) {
        // Thanks to the Stream, we are able to return our List in one go, instead of creating a modifiable Map
        // and then conditionally putting elements to it with for... put.
        return ImmutableMap.copyOf(states.stream()
                // Our tokens surely have repeated issuers, so we have more than 1 state per issuer. We still want to
                // file our tokens per issuer, so we are going to create a Map(issuer -> Sum of quantities).
                .collect(Collectors.toConcurrentMap(
                        // This is how to get the Map key for a given token.
                        TokenState::getIssuer,
                        // This is how to create a brand new Map value for a given token. Here, the quantity alone.
                        TokenState::getQuantity,
                        // This is how to merge 2 Map values that would otherwise conflict on a given key. We simply
                        // add the quantities. Plus, we want to fail hard in case of overflow.
                        Math::addExact)));
    }
}
