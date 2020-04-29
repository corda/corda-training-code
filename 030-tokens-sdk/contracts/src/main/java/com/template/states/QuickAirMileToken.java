package com.template.states;

import com.template.contracts.QuickAirMileContract;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * For demonstration purposes only.
 */
@BelongsToContract(QuickAirMileContract.class)
public class QuickAirMileToken implements FungibleAsset<QuickAirMileToken.AirMile> {

    public static class AirMile {}

    @NotNull
    private final Party issuer;
    @NotNull
    private final Amount<Issued<AirMile>> amount;
    @NotNull
    private final AbstractParty owner;

    public QuickAirMileToken(
            @NotNull final Party issuer,
            @NotNull final Amount<Issued<AirMile>> amount,
            @NotNull final AbstractParty owner) {
        this.issuer = issuer;
        this.amount = amount;
        this.owner = owner;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Collections.singletonList(owner);
    }

    @NotNull
    @Override
    public Amount<Issued<AirMile>> getAmount() {
        return amount;
    }

    @NotNull
    @Override
    public AbstractParty getOwner() {
        return owner;
    }

    @NotNull
    @Override
    public Collection<PublicKey> getExitKeys() {
        return Arrays.asList(issuer.getOwningKey(), owner.getOwningKey());
    }

    @NotNull
    @Override
    public QuickAirMileToken withNewOwnerAndAmount(
            @NotNull final Amount<Issued<AirMile>> newAmount,
            @NotNull final AbstractParty newOwner) {
        return new QuickAirMileToken(issuer, newAmount, newOwner);
    }

    @NotNull
    @Override
    public CommandAndState withNewOwner(@NotNull final AbstractParty newOwner) {
        throw new NotImplementedException("Needs contract command");
    }
}
