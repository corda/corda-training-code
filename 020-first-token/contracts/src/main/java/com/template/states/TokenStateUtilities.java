package com.template.states;

import com.sun.tools.javac.util.List;
import net.corda.core.identity.Party;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TokenStateUtilities {

    public static Map<Party, Long> mapSumByIssuer(final Collection<TokenState> states) {
        return states.stream()
                .collect(Collectors.toConcurrentMap(
                        TokenState::getIssuer,
                        state -> List.of(state.getQuantity()),
                        List::appendList))
                .entrySet()
                .stream()
                .map(entry -> new HashMap.SimpleEntry<>(
                        entry.getKey(),
                        entry.getValue()
                                .stream()
                                .reduce(0L, Math::addExact)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
