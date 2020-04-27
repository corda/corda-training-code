package com.template.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.TransactionUtilitiesKt;
import net.corda.core.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class AirMileType extends TokenType {

    public static final String IDENTIFIER = "AIR";
    public static final int FRACTION_DIGITS = 0;

    @NotNull
    public static SecureHash getContractAttachment() {
        //noinspection ConstantConditions
        return TransactionUtilitiesKt.getAttachmentIdForGenericParam(new AirMileType());
    }

    /**
     * This creates the {@link TokenType} for air-miles. Beware of the JAR hash issues.
     */
    public AirMileType() {
        super(IDENTIFIER, FRACTION_DIGITS);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash("AirMileType");
    }
}
