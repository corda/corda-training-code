package com.example.oracle;

import com.example.contract.FxContract.Commands.Swap;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.TimeWindow;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.FilteredTransaction;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

public class TemperatureOracleUtilities {

    @NotNull
    public static FilteredTransaction filter(
            @NotNull final SignedTransaction tx,
            @NotNull final AbstractParty oracle) {
        //noinspection rawtypes
        return tx.buildFilteredTransaction(element -> element instanceof TimeWindow
                || (element instanceof Command
                && ((Command) element).getSigners().contains(oracle.getOwningKey())
                && ((Command) element).getValue() instanceof Swap));
    }

}
