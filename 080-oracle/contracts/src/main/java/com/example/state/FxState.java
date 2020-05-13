package com.example.state;

import com.example.contract.FxContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@BelongsToContract(FxContract.class)
public class FxState implements ContractState {

    @NotNull
    private final List<AbstractParty> participants;

    public FxState(@NotNull final List<AbstractParty> participants) {
        //noinspection ConstantConditions
        if (participants == null) throw new NullPointerException("participants cannot be null");
        if (participants.isEmpty()) throw new IllegalArgumentException("participants cannot be empty");
        this.participants = participants;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return participants;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FxState that = (FxState) o;
        return participants.equals(that.participants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participants);
    }
}
