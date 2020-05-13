package com.example.state;

import com.example.contract.KYCContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@BelongsToContract(KYCContract.class)
public class KYCState implements LinearState {

    @NotNull
    private final UniqueIdentifier linearId;
    @NotNull
    private final Party issuer;
    @NotNull
    private final Party customer;
    private final boolean valid;

    @ConstructorForDeserialization
    public KYCState(@NotNull final UniqueIdentifier linearId,
                    @NotNull final Party issuer,
                    @NotNull final Party customer,
                    final boolean valid) {
        this.linearId = linearId;
        this.issuer = issuer;
        this.customer = customer;
        this.valid = valid;
    }

    public KYCState(@NotNull final Party issuer,
                    @NotNull final Party customer,
                    final boolean valid) {
        this.linearId = new UniqueIdentifier();
        this.issuer = issuer;
        this.customer = customer;
        this.valid = valid;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Arrays.asList(issuer, customer);
    }

    @NotNull
    public Party getIssuer() {
        return issuer;
    }

    @NotNull
    public Party getCustomer() {
        return customer;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final KYCState kycState = (KYCState) o;
        return valid == kycState.valid &&
                linearId.equals(kycState.linearId) &&
                issuer.equals(kycState.issuer) &&
                customer.equals(kycState.customer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linearId, issuer, customer, valid);
    }
}