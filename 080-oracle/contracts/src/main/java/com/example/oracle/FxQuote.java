package com.example.oracle;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import net.corda.core.serialization.CordaSerializable;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@CordaSerializable
public class FxQuote {

    @NotNull
    private final TokenType base;
    @NotNull
    private final TokenType counter;
    @NotNull
    private final BigDecimal rate;
    @NotNull
    private final Instant expirationDate;

    public FxQuote(
            @NotNull final TokenType base,
            @NotNull final TokenType counter,
            @NotNull final BigDecimal rate,
            @NotNull final Instant expirationDate) {
        //noinspection ConstantConditions
        if (base == null) throw new NullPointerException("base cannot be null");
        //noinspection ConstantConditions
        if (counter == null) throw new NullPointerException("counter cannot be null");
        //noinspection ConstantConditions
        if (rate == null) throw new NullPointerException("rate cannot be null");
        //noinspection ConstantConditions
        if (expirationDate == null) throw new NullPointerException("expirationDate cannot be null");
        this.base = base;
        this.counter = counter;
        this.rate = rate;
        this.expirationDate = expirationDate;
    }

    @NotNull
    public TokenType getBase() {
        return base;
    }

    @NotNull
    public TokenType getCounter() {
        return counter;
    }

    @NotNull
    public BigDecimal getRate() {
        return rate;
    }

    @NotNull
    public Instant getExpirationDate() {
        return expirationDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FxQuote fxQuote = (FxQuote) o;
        return base.equals(fxQuote.base) &&
                counter.equals(fxQuote.counter) &&
                rate.equals(fxQuote.rate) &&
                expirationDate.equals(fxQuote.expirationDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, counter, rate, expirationDate);
    }
}
