package com.template.states;

import com.google.common.collect.ImmutableMap;
import net.corda.core.identity.Party;

import java.util.*;
import java.util.stream.Collectors;

public interface TokenStateUtilities {

    static List<Long> getQuantityAsSingletonList(final TokenState state) {
        return Collections.singletonList(state.getQuantity());
    }

    static <T> List<T> merge(final List<T> left, final List<T> right) {
        final List<T> merged = new ArrayList<>(left);
        merged.addAll(right);
        return merged;
    }

    static <K> Map.Entry<K, Long> mapSumValues(final Map.Entry<K, List<Long>> entry) {
        return new HashMap.SimpleEntry<>(
                entry.getKey(),
                sumExact(entry.getValue()));
    }

    static Long sumExact(final List<Long> values) {
        return values.stream().reduce(0L, Math::addExact);
    }

    static Map<Party, Long> mapSumByIssuer(final Collection<TokenState> states) {
        // Thanks to the Stream, we are able to return our List in one go, instead of creating a modifiable
        // one and then conditionally adding elements to it with for... add.
        return ImmutableMap.copyOf(states.stream()
                // Our tokens surely have repeated issuers, so we have more than 1 state per issuer. We still want to
                // file our tokens per issuer, so we are going to create a Map(issuer -> List of quantities).
                .collect(Collectors.toConcurrentMap(
                        // This is how to get the Map key for a given token.
                        TokenState::getIssuer,
                        // This is how to create a brand new Map value for a given token. Here, the quantity alone
                        // in a list.
                        TokenStateUtilities::getQuantityAsSingletonList,
                        // This is how to merge 2 Map values that would otherwise conflict on a given key. We simply
                        // merge the lists of quantities.
                        TokenStateUtilities::merge))
                // Now that we have a Map, let's take the entries Map.Entry<Party, List<Long>>.
                .entrySet()
                // And again stream to easily convert the elements.
                .stream()
                // On each entry, we get the sum on the value, which is a List of long.
                .map(TokenStateUtilities::mapSumValues)
                // Now file back into a Map.
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
