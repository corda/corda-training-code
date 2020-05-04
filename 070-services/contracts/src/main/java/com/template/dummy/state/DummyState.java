package com.template.dummy.state;

import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@BelongsToContract(DummyContract.class)
public class DummyState implements ContractState {
    @NotNull
    private final AbstractParty lost;
    @NotNull
    private final AbstractParty seen;

    public DummyState(@NotNull final AbstractParty lost, @NotNull final AbstractParty seen) {
        this.lost = lost;
        this.seen = seen;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Collections.singletonList(seen);
    }

    @NotNull
    public AbstractParty getLost() {
        return lost;
    }

    @NotNull
    public AbstractParty getSeen() {
        return seen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DummyState that = (DummyState) o;
        return lost.equals(that.lost) &&
                seen.equals(that.seen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lost, seen);
    }
}
