package com.template.proposal.state;

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import net.corda.core.contracts.*;
import net.corda.core.crypto.SecureHash;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.core.TestIdentity;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static org.junit.Assert.*;

public class SalesProposalTests {

    private final Party notary = new TestIdentity(
            new CordaX500Name("Notary", "Washington D.C.", "US")).getParty();
    private final Party usMint = new TestIdentity(
            new CordaX500Name("US Mint", "Washington D.C.", "US")).getParty();
    private final AbstractParty alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB")).getParty();
    private final AbstractParty bob = new TestIdentity(
            new CordaX500Name("Bob", "New York", "US")).getParty();
    private final AbstractParty carly = new TestIdentity(
            new CordaX500Name("Carly", "New York", "US")).getParty();
    private final TokenType usd = FiatCurrency.Companion.getInstance("USD");
    private final IssuedTokenType mintUsd = new IssuedTokenType(usMint, usd);
    private final Amount<IssuedTokenType> amount1 = new Amount<>(15L, mintUsd);
    private final Amount<IssuedTokenType> amount2 = new Amount<>(20L, mintUsd);
    private final NonFungibleToken aliceNFToken = new NonFungibleToken(
            mintUsd, alice, new UniqueIdentifier(), null);
    private final NonFungibleToken bobNFToken = new NonFungibleToken(
            mintUsd, bob, new UniqueIdentifier(), null);
    private final StateAndRef<NonFungibleToken> aliceRef1 = new StateAndRef<>(
            new TransactionState<>(aliceNFToken, notary),
            new StateRef(SecureHash.randomSHA256(), 0));
    private final StateAndRef<NonFungibleToken> aliceRef2 = new StateAndRef<>(
            new TransactionState<>(aliceNFToken, notary),
            new StateRef(SecureHash.randomSHA256(), 1));
    private final StateAndRef<NonFungibleToken> bobRef1 = new StateAndRef<>(
            new TransactionState<>(bobNFToken, notary),
            new StateRef(SecureHash.randomSHA256(), 0));
    private final Instant oneMinuteAway = Instant.now().plus(Duration.ofMinutes(1));

    @Test(expected = NullPointerException.class)
    public void cannotConstructWithNullLinearId() {
        //noinspection ConstantConditions
        new SalesProposal(null, aliceRef1, bob, amount1, oneMinuteAway);
    }

    @Test(expected = NullPointerException.class)
    public void cannotConstructWithNullAsset() {
        //noinspection ConstantConditions
        new SalesProposal(new UniqueIdentifier(), null, bob, amount1, oneMinuteAway);
    }

    @Test(expected = NullPointerException.class)
    public void cannotConstructWithNullBuyer() {
        //noinspection ConstantConditions
        new SalesProposal(new UniqueIdentifier(), aliceRef1, null, amount1, oneMinuteAway);
    }

    @Test(expected = NullPointerException.class)
    public void cannotConstructWithNullPrice() {
        //noinspection ConstantConditions
        new SalesProposal(new UniqueIdentifier(), aliceRef1, bob, null, oneMinuteAway);
    }

    @Test(expected = NullPointerException.class)
    public void cannotConstructWithNullExpirationDate() {
        //noinspection ConstantConditions
        new SalesProposal(new UniqueIdentifier(), aliceRef1, bob, amount1, null);
    }

    @Test
    public void canConstructWithSameSellerAndBuyer() {
        final SalesProposal proposal = new SalesProposal(new UniqueIdentifier(), aliceRef1,
                alice, amount1, oneMinuteAway);
        assertEquals(proposal.getSeller(), proposal.getBuyer());
    }

    @Test
    public void participantsAreBoth() {
        final SalesProposal proposal = new SalesProposal(new UniqueIdentifier(), aliceRef1,
                bob, amount1, oneMinuteAway);
        assertEquals(proposal.getParticipants(), Arrays.asList(alice, bob));
    }

    @Test
    public void gettersWork() {
        final UniqueIdentifier linearId = new UniqueIdentifier();
        final SalesProposal proposal = new SalesProposal(linearId, aliceRef1, carly, amount1, oneMinuteAway);
        assertEquals(linearId, proposal.getLinearId());
        assertTrue(proposal.isSameAsset(aliceRef1));
        assertEquals(alice, proposal.getSeller());
        assertEquals(carly, proposal.getBuyer());
        assertEquals(amount1, proposal.getPrice());
        assertEquals(oneMinuteAway, proposal.getExpirationDate());
    }

    @Test
    public void equalsDependsOnAllElements() {
        final UniqueIdentifier linearId1 = new UniqueIdentifier();
        final UniqueIdentifier linearId2 = new UniqueIdentifier();
        assertEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway),
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway));
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway),
                new SalesProposal(linearId2, aliceRef1, bob, amount1, oneMinuteAway));
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway),
                new SalesProposal(linearId1, aliceRef2, bob, amount1, oneMinuteAway));
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, carly, amount1, oneMinuteAway),
                new SalesProposal(linearId1, bobRef1, carly, amount1, oneMinuteAway));
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway),
                new SalesProposal(linearId1, aliceRef1, carly, amount1, oneMinuteAway));
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway),
                new SalesProposal(linearId1, aliceRef1, bob, amount2, oneMinuteAway));
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway),
                new SalesProposal(linearId1, aliceRef1, bob, amount1,
                        Instant.now().plus(2, ChronoUnit.MINUTES)));
    }

    @Test
    public void hashCodeDependsOnAllElements() {
        final UniqueIdentifier linearId1 = new UniqueIdentifier();
        final UniqueIdentifier linearId2 = new UniqueIdentifier();
        assertEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway).hashCode(),
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway).hashCode());
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway).hashCode(),
                new SalesProposal(linearId2, aliceRef1, bob, amount1, oneMinuteAway).hashCode());
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway).hashCode(),
                new SalesProposal(linearId1, aliceRef2, bob, amount1, oneMinuteAway).hashCode());
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, carly, amount1, oneMinuteAway).hashCode(),
                new SalesProposal(linearId1, bobRef1, carly, amount1, oneMinuteAway).hashCode());
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway).hashCode(),
                new SalesProposal(linearId1, aliceRef1, carly, amount1, oneMinuteAway).hashCode());
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway).hashCode(),
                new SalesProposal(linearId1, aliceRef1, bob, amount2, oneMinuteAway).hashCode());
        assertNotEquals(
                new SalesProposal(linearId1, aliceRef1, bob, amount1, oneMinuteAway).hashCode(),
                new SalesProposal(linearId1, aliceRef1, bob, amount1,
                        Instant.now().plus(2, ChronoUnit.MINUTES)).hashCode());
    }

}