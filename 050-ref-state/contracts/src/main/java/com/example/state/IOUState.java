package com.example.state;

import com.example.contract.IOUContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The state object recording IOU agreements between two parties.
 * <p>
 * A state must implement [ContractState] or one of its descendants.
 */
@BelongsToContract(IOUContract.class)
public class IOUState implements LinearState {
    private final Integer value;
    private final Party lender;
    private final Party borrower;
    private final Party kycIssuer;
    private final UniqueIdentifier linearId;

    /**
     * @param value    the value of the IOU.
     * @param lender   the party issuing the IOU.
     * @param borrower the party receiving and approving the IOU.
     * @param kycIssuer the party providing the KYC.
     */
    public IOUState(final int value,
                    @NotNull final Party lender,
                    @NotNull final Party borrower,
                    @NotNull final Party kycIssuer,
                    @NotNull final UniqueIdentifier linearId) {
        this.value = value;
        this.lender = lender;
        this.borrower = borrower;
        this.kycIssuer = kycIssuer;
        this.linearId = linearId;
    }

    @NotNull
    public Integer getValue() {
        return value;
    }

    @NotNull
    public Party getLender() {
        return lender;
    }

    @NotNull
    public Party getBorrower() {
        return borrower;
    }

    @NotNull
    public Party getKycIssuer() {
        return kycIssuer;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(lender, borrower);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final IOUState iouState = (IOUState) o;
        return Objects.equals(value, iouState.value) &&
                Objects.equals(lender, iouState.lender) &&
                Objects.equals(borrower, iouState.borrower) &&
                Objects.equals(kycIssuer, iouState.kycIssuer) &&
                Objects.equals(linearId, iouState.linearId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, lender, borrower, kycIssuer, linearId);
    }

    @Override
    public String toString() {
        return "IOUState{" +
                "value=" + value +
                ", lender=" + lender +
                ", borrower=" + borrower +
                ", kycIssuer=" + kycIssuer +
                ", linearId=" + linearId +
                '}';
    }
}