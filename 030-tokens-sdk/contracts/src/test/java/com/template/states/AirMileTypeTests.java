package com.template.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AirMileTypeTests {

    @Test
    public void hashCodeIsConstant() {
        assertEquals(new AirMileType().hashCode(), new AirMileType().hashCode());
    }

    @Test
    public void equalsIsOkWithSame() {
        assertEquals(new AirMileType(), new AirMileType());
    }

    @Test
    public void equalsIsDifferentWithNull() {
        assertNotEquals(new AirMileType(), null);
    }

    @Test
    public void equalsIsDifferentWithOtherTokenType() {
        assertNotEquals(new AirMileType(), new TokenType(AirMileType.IDENTIFIER, AirMileType.FRACTION_DIGITS));
    }

}
