package com.template.usd;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.money.DigitalCurrency;
import net.corda.core.contracts.Amount;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CurrencyLearningTest {

    public CurrencyLearningTest() {
    }

    @Test(expected = ArithmeticException.class)
    public void cannotRepresentTenEth() {
        final TokenType etherType = DigitalCurrency.Companion.getInstance("ETH");
        final Amount<TokenType> oneEther = AmountUtilitiesKt.amount(1, etherType);

        // One Ether in Wei is 19 digits long.
        assertEquals(19, String.valueOf(oneEther.getQuantity()).length());
        // 19 is also the maximum number of digits that a "long" can hold!
        assertEquals(19, String.valueOf(Long.MAX_VALUE).length());

        // Now let's try to create 10 Ether,
        // which in Wei is 20 digits long and cannot be represented by "long" type.
        AmountUtilitiesKt.amount(10, etherType);
    }
}
