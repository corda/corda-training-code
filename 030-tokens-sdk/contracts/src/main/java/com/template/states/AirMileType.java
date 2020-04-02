package com.template.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import org.jetbrains.annotations.NotNull;

public class AirMileType {

    public static final String IDENTIFIER = "AIR";
    public static final int FRACTION_DIGITS = 0;

    @NotNull
    public static TokenType create() {
        return new TokenType(IDENTIFIER, FRACTION_DIGITS);
    }
}
