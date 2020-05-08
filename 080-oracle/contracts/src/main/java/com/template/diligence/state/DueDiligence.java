package com.template.diligence.state;

import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@BelongsToContract(DueDiligenceContract.class)
public class DueDiligence implements LinearState {

    @NotNull
    private final UniqueIdentifier linearId;
    @NotNull
    private final UniqueIdentifier tokenId;
    @NotNull
    private final AbstractParty oracle;
    @NotNull
    private final List<AbstractParty> participants;

    public DueDiligence(
            @NotNull final UniqueIdentifier linearId,
            @NotNull final UniqueIdentifier tokenId,
            @NotNull final AbstractParty oracle,
            @NotNull final List<AbstractParty> participants) {
        //noinspection ConstantConditions
        if (linearId == null) throw new NullPointerException("linearId cannot be null");
        //noinspection ConstantConditions
        if (tokenId == null) throw new NullPointerException("tokenId cannot be null");
        //noinspection ConstantConditions
        if (oracle == null) throw new NullPointerException("oracle cannot be null");
        //noinspection ConstantConditions
        if (participants == null) throw new NullPointerException("participants cannot be null");
        this.linearId = linearId;
        this.tokenId = tokenId;
        this.oracle = oracle;
        this.participants = participants;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @NotNull
    public UniqueIdentifier getTokenId() {
        return tokenId;
    }

    @NotNull
    public AbstractParty getOracle() {
        return oracle;
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
        final DueDiligence that = (DueDiligence) o;
        return linearId.equals(that.linearId) &&
                tokenId.equals(that.tokenId) &&
                oracle.equals(that.oracle) &&
                participants.equals(that.participants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linearId, tokenId, oracle, participants);
    }
}
