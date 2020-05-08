package com.template.diligence.state;

import net.corda.core.contracts.Command;
import net.corda.core.contracts.TimeWindow;
import net.corda.core.identity.AbstractParty;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.FilteredTransaction;
import net.corda.core.transactions.WireTransaction;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class DiligenceOracleUtilities {

    public static final Duration VALID_DURATION = Duration.ofMinutes(10);

    @CordaSerializable
    public enum Status {
        Linked, Clear
    }

    @NotNull
    public static FilteredTransaction filter(
            @NotNull final WireTransaction tx,
            @NotNull final AbstractParty oracle) {
        return tx.buildFilteredTransaction(element -> {
            //noinspection rawtypes
            return (element instanceof Command)
                    && ((Command) element).getSigners().contains(oracle.getOwningKey())
                    && (((Command) element).getValue() instanceof DueDiligenceContract.Commands.Certify)
                    || element instanceof TimeWindow;
        });
    }

}
