package com.template.states;

import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * For demonstration purposes only.
 */
@SuppressWarnings("unused")
public class TrialToken<T> implements FungibleState<Issued<T>>, OwnableState {

    @NotNull
    private final Amount<Issued<T>> amount;
    @NotNull
    private final AbstractParty owner;

    public TrialToken(
            @NotNull final Amount<Issued<T>> amount,
            @NotNull final AbstractParty owner) {
        this.amount = amount;
        this.owner = owner;
    }

    @NotNull
    @Override
    public Amount<Issued<T>> getAmount() {
        return amount;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Collections.singletonList(owner);
    }

    @NotNull
    @Override
    public AbstractParty getOwner() {
        return owner;
    }

    @NotNull
    public AbstractParty getIssuer() {
        return amount.getToken().getIssuer().getParty();
    }

    @NotNull
    public T getProduct() {
        return amount.getToken().getProduct();
    }

    @NotNull
    @Override
    public CommandAndState withNewOwner(@NotNull final AbstractParty newOwner) {
        throw new NotImplementedException("We need a contract command");
    }
}
