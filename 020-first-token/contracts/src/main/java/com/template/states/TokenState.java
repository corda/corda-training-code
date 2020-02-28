package com.template.states;

import com.template.contracts.TokenContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@BelongsToContract(TokenContract.class)
public final class TokenState implements ContractState {

    @NotNull
    private final Party issuer;
    @NotNull
    private final Party owner;
    private final long amount;

    public TokenState(@NotNull Party issuer, @NotNull Party owner, long amount) {
        //noinspection ConstantConditions
        if (issuer == null) throw new NullPointerException("issuer cannot be null");
        //noinspection ConstantConditions
        if (owner == null) throw new NullPointerException("owner cannot be null");
        this.issuer = issuer;
        this.owner = owner;
        if (amount <= 0) throw new IllegalArgumentException("amount must be above 0");
        this.amount = amount;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Collections.singletonList(owner);
    }

    @NotNull
    public Party getIssuer() {
        return issuer;
    }

    @NotNull
    public Party getOwner() {
        return owner;
    }

    public long getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenState that = (TokenState) o;
        return amount == that.amount &&
                issuer.equals(that.issuer) &&
                owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, owner, amount);
    }

    @Override
    public String toString() {
        return "TokenState{" +
                "issuer=" + issuer +
                ", owner=" + owner +
                ", amount=" + amount +
                '}';
    }
}