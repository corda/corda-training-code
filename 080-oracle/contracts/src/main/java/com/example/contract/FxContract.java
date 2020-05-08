package com.example.contract;

import com.example.oracle.FxQuote;
import com.example.state.FxState;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class FxContract implements Contract {
    public static final String ID = "com.example.contract.FxContract";

    @Override
    public void verify(@NotNull final LedgerTransaction tx) throws IllegalArgumentException {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final List<FxState> fxStates = tx.inputsOfType(FxState.class);
        final List<FungibleToken> inputs = tx.inputsOfType(FungibleToken.class);

        if (command.getValue() instanceof Commands.Swap) {
            final FxQuote quote = ((Commands.Swap) command.getValue()).quote;
            final long baseInputTotal = inputs.stream()
                    .filter(it -> it.getTokenType().equals(quote.getBase()))
                    .map(it -> it.getAmount().getQuantity())
                    .reduce(0L, Math::addExact);
            final long counterInputTotal = inputs.stream()
                    .filter(it -> it.getTokenType().equals(quote.getCounter()))
                    .map(it -> it.getAmount().getQuantity())
                    .reduce(0L, Math::addExact);
            requireThat(req -> {
                req.using("There should be a single FxOracleState", fxStates.size() == 1);
                final FxState fxState = fxStates.get(0);
                req.using("There should be no FxOracleStates in output",
                        tx.outputsOfType(FxState.class).isEmpty());
                req.using("The inputs should have the right ratio",
                        BigDecimal.valueOf(counterInputTotal)
                                .divide(BigDecimal.valueOf(baseInputTotal), RoundingMode.HALF_EVEN)
                                .equals(quote.getRate()));
                //noinspection ConstantConditions
                req.using("The quote should not have expired",
                        tx.getTimeWindow().getUntilTime().isBefore(quote.getExpirationDate()));
                return null;
            });
        } else {
            throw new IllegalArgumentException("Unknown command " + command.getValue());
        }
    }

    public interface Commands extends CommandData {

        class Swap implements Commands {
            @NotNull
            private final FxQuote quote;

            public Swap(@NotNull final FxQuote quote) {
                //noinspection ConstantConditions
                if (quote == null) throw new NullPointerException("quote cannot be null");
                this.quote = quote;
            }

            @NotNull
            public FxQuote getQuote() {
                return quote;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                final Swap swap = (Swap) o;
                return quote.equals(swap.quote);
            }

            @Override
            public int hashCode() {
                return Objects.hash(quote);
            }
        }
    }

}
