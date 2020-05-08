package com.example.contract;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Objects;

public class TemperatureContract implements Contract {
    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        // TODO
    }

    public interface Commands extends CommandData {
        class HowWarm implements Commands {
            @NotNull
            private final BigDecimal lowBound;
            @NotNull
            private final BigDecimal highBound;

            public HowWarm(
                    @NotNull final BigDecimal lowBound,
                    @NotNull final BigDecimal highBound) {
                //noinspection ConstantConditions
                if (lowBound == null) throw new NullPointerException("lowBound cannot be null");
                //noinspection ConstantConditions
                if (highBound == null) throw new NullPointerException("highBound cannot be null");
                this.lowBound = lowBound;
                this.highBound = highBound;
            }

            @NotNull
            public BigDecimal getLowBound() {
                return lowBound;
            }

            @NotNull
            public BigDecimal getHighBound() {
                return highBound;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                final HowWarm howWarm = (HowWarm) o;
                return lowBound.equals(howWarm.lowBound) &&
                        highBound.equals(howWarm.highBound);
            }

            @Override
            public int hashCode() {
                return Objects.hash(lowBound, highBound);
            }
        }
    }
}
