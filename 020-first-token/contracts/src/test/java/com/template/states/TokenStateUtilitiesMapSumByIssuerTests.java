package com.template.states;

import com.google.common.collect.ImmutableMap;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.core.TestIdentity;
import org.junit.Test;

import java.util.*;

import static com.template.states.TokenStateUtilities.mapSumByIssuer;
import static org.junit.Assert.assertEquals;

public class TokenStateUtilitiesMapSumByIssuerTests {

    private final Party alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB")).getParty();
    private final Party bob = new TestIdentity(new CordaX500Name("Bob", "London", "GB")).getParty();
    private final Party carly = new TestIdentity(new CordaX500Name("Carly", "London", "GB")).getParty();

    @Test
    public void mapSumByIssuerGetsSameValueOnSingleton() {
        final Map<Party, Long> mappedSums = mapSumByIssuer(Collections.singletonList(new TokenState(alice, bob, 10)));
        assertEquals(1, mappedSums.size());
        assertEquals(10L, mappedSums.get(alice).longValue());
    }

    @Test
    public void mapSumByIssuerGetsSumOnUniqueIssuer() {
        final Map<Party, Long> mappedSums = mapSumByIssuer(Arrays.asList(
                new TokenState(alice, bob, 10),
                new TokenState(alice, carly, 15)));
        assertEquals(1, mappedSums.size());
        assertEquals(25L, mappedSums.get(alice).longValue());
    }

    @Test
    public void mapSumByIssuerGetsSumForEachIssuer() {
        final Map<Party, Long> mappedSums = mapSumByIssuer(Arrays.asList(
                new TokenState(alice, bob, 10),
                new TokenState(alice, carly, 15),
                new TokenState(carly, bob, 30),
                new TokenState(carly, carly, 25),
                new TokenState(carly, alice, 2)));
        assertEquals(2, mappedSums.size());
        assertEquals(25L, mappedSums.get(alice).longValue());
        assertEquals(57L, mappedSums.get(carly).longValue());
    }

    @Test(expected = ArithmeticException.class)
    public void overflowTriggersErrorInMapSumByIssuer() {
        mapSumByIssuer(Arrays.asList(
                new TokenState(alice, bob, Long.MAX_VALUE),
                new TokenState(alice, carly, 1)));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void mapSumByIssuerIsImmutable() {
        final Map<Party, Long> mappedSums = mapSumByIssuer(Collections.singletonList(new TokenState(alice, bob, 10)));
        //noinspection ConstantConditions
        mappedSums.put(alice, 20L);
    }

    /**
     * This function is given to help understand the Stream way of doing in {@link TokenStateUtilities#mapSumByIssuer}.
     */
    private Map<Party, Long> standardMapSumByIssuer(final Collection<TokenState> states) {
        final HashMap<Party, Long> working = new HashMap<>();
        for (TokenState state : states) {
            working.merge(state.getHolder(), state.getQuantity(), Math::addExact);
        }
        return ImmutableMap.copyOf(working);
    }

    @Test
    public void compareWithStandard() {
        final Map<Party, Long> mappedSums1 = standardMapSumByIssuer(Arrays.asList(
                new TokenState(alice, bob, 10),
                new TokenState(alice, carly, 15),
                new TokenState(carly, bob, 30),
                new TokenState(carly, carly, 25),
                new TokenState(carly, alice, 2)));
        final Map<Party, Long> mappedSums2 = standardMapSumByIssuer(Arrays.asList(
                new TokenState(alice, bob, 10),
                new TokenState(alice, carly, 15),
                new TokenState(carly, bob, 30),
                new TokenState(carly, carly, 25),
                new TokenState(carly, alice, 2)));
        assertEquals(mappedSums1.size(), mappedSums2.size());
        assertEquals(mappedSums1.get(alice).longValue(), mappedSums2.get(alice).longValue());
        assertEquals(mappedSums1.get(carly).longValue(), mappedSums2.get(carly).longValue());
    }

}
