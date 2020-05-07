package com.template.proposal.state;

import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class DueDiligence implements ContractState {

    @NotNull
    private final UUID tokenId;
    @NotNull
    private final List<AbstractParty> partipants;

    public DueDiligence(
            @NotNull final UUID tokenId,
            @NotNull final List<AbstractParty> partipants) {
        //noinspection ConstantConditions
        if (tokenId == null) throw new NullPointerException("tokenId cannot be null");
        //noinspection ConstantConditions
        if (partipants == null) throw new NullPointerException("partipants cannot be null");
        this.tokenId = tokenId;
        this.partipants = partipants;
    }

    @NotNull
    public UUID getTokenId() {
        return tokenId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return partipants;
    }
}
