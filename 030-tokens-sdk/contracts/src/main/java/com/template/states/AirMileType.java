package com.template.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public final class AirMileType {

    public static final String IDENTIFIER = "AIR";
    public static final int FRACTION_DIGITS = 0;

    private AirMileType() {
        // Do not instantiate
        throw new NotImplementedException();
    }

    /**
     * This creates the {@link TokenType} for air-miles. We do not create instances of {@link AirMileType} as this can
     * create issues of JAR hash.
     */
    @NotNull
    public static TokenType create() {
        return new TokenType(IDENTIFIER, FRACTION_DIGITS);
    }
}
