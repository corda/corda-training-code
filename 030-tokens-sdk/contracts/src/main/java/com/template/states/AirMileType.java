package com.template.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import org.jetbrains.annotations.NotNull;

public class AirMileType extends TokenType {

    public static final String IDENTIFIER = "AIR";
    public static final int FRACTION_DIGITS = 0;

    public AirMileType() {
        super(IDENTIFIER, FRACTION_DIGITS);
    }
}
