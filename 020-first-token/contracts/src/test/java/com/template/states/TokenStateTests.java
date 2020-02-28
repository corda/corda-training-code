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
    public void doesNotAccept0Amount() {
        new TokenState(alice, bob, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void doesNotAcceptNegativeAmount() {
        new TokenState(alice, bob, -1);
    }

    @Test
    public void equalsAndHashcodeIdentifyIdenticalInstances() {
        final TokenState token1 = new TokenState(alice, bob, 2);
        final TokenState token2 = new TokenState(alice, bob, 2);
        assertEquals(token1, token2);
        assertEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    public void equalsAndHashcodeDifferentiateByIssuer() {
        final TokenState token1 = new TokenState(alice, bob, 2);
        final TokenState token2 = new TokenState(carly, bob, 2);
        assertNotEquals(token1, token2);
        assertNotEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    public void equalsAndHashcodeDifferentiateByOwner() {
        final TokenState token1 = new TokenState(alice, bob, 2);
        final TokenState token2 = new TokenState(alice, carly, 2);
        assertNotEquals(token1, token2);
        assertNotEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    public void equalsAndHashcodeDifferentiateByAmount() {
        final TokenState token1 = new TokenState(alice, bob, 2);
        final TokenState token2 = new TokenState(alice, bob, 3);
        assertNotEquals(token1, token2);
        assertNotEquals(token1.hashCode(), token2.hashCode());
    }
}
