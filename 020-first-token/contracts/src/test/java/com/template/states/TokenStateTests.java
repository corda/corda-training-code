package com.template.states;

import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.core.TestIdentity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TokenStateTests {

    private final Party alice = new TestIdentity(new CordaX500Name("Alice", "London", "GB")).getParty();
    private final Party bob = new TestIdentity(new CordaX500Name("Bob", "London", "GB")).getParty();
    private final Party carly = new TestIdentity(new CordaX500Name("Carly", "London", "GB")).getParty();

    @Test(expected = IllegalArgumentException.class)
    public void doesNotAcceptNullIssuer() {
        //noinspection ConstantConditions
        new TokenState(null, bob, 2L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void doesNotAcceptNullOwner() {
        //noinspection ConstantConditions
        new TokenState(alice, null, 2L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void doesNotAccept0Amount() {
        new TokenState(alice, bob, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void doesNotAcceptNegativeAmount() {
        new TokenState(alice, bob, -1L);
    }

    @Test
    public void constructorAndGettersAreWorking() {
        final TokenState state = new TokenState(alice, bob, 2L);
        assertEquals(alice, state.getIssuer());
        assertEquals(bob, state.getOwner());
        assertEquals(2L, state.getAmount());
    }

    @Test
    public void equalsAndHashcodeIdentifyIdenticalInstances() {
        final TokenState token1 = new TokenState(alice, bob, 2L);
        final TokenState token2 = new TokenState(alice, bob, 2L);
        assertEquals(token1, token2);
        assertEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    public void equalsAndHashcodeDifferentiateByIssuer() {
        final TokenState token1 = new TokenState(alice, bob, 2L);
        final TokenState token2 = new TokenState(carly, bob, 2L);
        assertNotEquals(token1, token2);
        assertNotEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    public void equalsAndHashcodeDifferentiateByOwner() {
        final TokenState token1 = new TokenState(alice, bob, 2L);
        final TokenState token2 = new TokenState(alice, carly, 2L);
        assertNotEquals(token1, token2);
        assertNotEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    public void equalsAndHashcodeDifferentiateByAmount() {
        final TokenState token1 = new TokenState(alice, bob, 2L);
        final TokenState token2 = new TokenState(alice, bob, 3L);
        assertNotEquals(token1, token2);
        assertNotEquals(token1.hashCode(), token2.hashCode());
    }
}
