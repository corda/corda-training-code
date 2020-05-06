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
            private final BigDecimal temperature;
            @NotNull
            private final BigDecimal tolerance;

            public HowWarm(
                    @NotNull final BigDecimal temperature,
                    @NotNull final BigDecimal tolerance) {
                this.temperature = temperature;
                this.tolerance = tolerance;
            }

            @NotNull
            public BigDecimal getTemperature() {
                return temperature;
            }

            @NotNull
            public BigDecimal getTolerance() {
                return tolerance;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                final HowWarm howWarm = (HowWarm) o;
                return temperature.equals(howWarm.temperature) &&
                        tolerance.equals(howWarm.tolerance);
            }

            @Override
            public int hashCode() {
                return Objects.hash(temperature, tolerance);
            }
        }
    }
}
