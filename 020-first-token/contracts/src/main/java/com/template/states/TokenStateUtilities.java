package com.template.states;

import com.google.common.collect.ImmutableMap;
import net.corda.core.identity.Party;

import java.util.*;
import java.util.stream.Collectors;

public class TokenStateUtilities {

    private static List<Long> getQuantityAsSingletonList(final TokenState state) {
        return Collections.singletonList(state.getQuantity());
    }

    private static <T> List<T> merge(final List<T> left, final List<T> right) {
        final List<T> merged = new ArrayList<>(left);
        merged.addAll(right);
        return merged;
    }

    private static <K> Map.Entry<K, Long> mapSumValues(final Map.Entry<K, List<Long>> entry) {
        return new HashMap.SimpleEntry<>(
                entry.getKey(),
                sumExact(entry.getValue()));
    }

    private static Long sumExact(final List<Long> values) {
        return values.stream().reduce(0L, Math::addExact);
    }

    public static Map<Party, Long> mapSumByIssuer(final Collection<TokenState> states) {
        return ImmutableMap.copyOf(states.stream()
                .collect(Collectors.toConcurrentMap(
                        TokenState::getIssuer,
                        TokenStateUtilities::getQuantityAsSingletonList,
                        TokenStateUtilities::merge))
                .entrySet()
                .stream()
                .map(TokenStateUtilities::mapSumValues)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
