package com.template.diligence.state;

import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.core.TestIdentity;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class DueDiligenceTests {

    private final Party dmv = new TestIdentity(
            new CordaX500Name("DMV", "Austin", "US")).getParty();
    private final AbstractParty alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB")).getParty();
    private final AbstractParty bob = new TestIdentity(
            new CordaX500Name("Bob", "New York", "US")).getParty();
    private final AbstractParty carly = new TestIdentity(
            new CordaX500Name("Carly", "New York", "US")).getParty();

    @Test(expected = IllegalArgumentException.class)
    public void participantsCannotBeEmpty() {
        new DueDiligence(new UniqueIdentifier(), new UniqueIdentifier(), dmv, Collections.emptyList());
    }

    @Test
    public void participantsCanBeSingle() {
        final DueDiligence dueDil = new DueDiligence(new UniqueIdentifier(), new UniqueIdentifier(),
                dmv, Collections.singletonList(alice));
        assertEquals(Collections.singletonList(alice), dueDil.getParticipants());
    }

    @Test
    public void participantsCanBeTwo() {
        final DueDiligence dueDil = new DueDiligence(new UniqueIdentifier(), new UniqueIdentifier(),
                dmv, Arrays.asList(alice, bob));
        assertEquals(Arrays.asList(alice, bob), dueDil.getParticipants());
    }

    @Test
    public void participantsCanBeThree() {
        final DueDiligence dueDil = new DueDiligence(new UniqueIdentifier(), new UniqueIdentifier(),
                dmv, Arrays.asList(alice, bob, carly));
        assertEquals(Arrays.asList(alice, bob, carly), dueDil.getParticipants());
    }

}
