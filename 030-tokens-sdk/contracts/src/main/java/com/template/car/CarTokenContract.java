package com.template.car;

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class CarTokenContract extends EvolvableTokenContract implements Contract {

    @Override
    public void additionalCreateChecks(@NotNull final LedgerTransaction tx) {
        // Outputs have already been checked as counting exactly 1. If it fails here, it means the output is not
        // a CarTokenType.
        final CarTokenType outputCarTokenType = tx.outputsOfType(CarTokenType.class).get(0);
        requireThat(require -> {
            // Validation rules on our fields.
            require.using("Mileage must start at 0.",
                    outputCarTokenType.getMileage() == 0L);
            require.using("Price cannot be 0.",
                    outputCarTokenType.getPrice() > 0L);
            return null;
        });
    }

    @Override
    public void additionalUpdateChecks(@NotNull final LedgerTransaction tx) {
        // Inputs and outputs have already been checked as counting exactly 1 each. If any fails here, it means the
        // input or output is not a CarTokenType.
        final CarTokenType inputCarTokenType = tx.inputsOfType(CarTokenType.class).get(0);
        final CarTokenType outputCarTokenType = tx.outputsOfType(CarTokenType.class).get(0);
        requireThat(require -> {
            // Validation rules on our fields.
            require.using("VIN cannot be updated.",
                    outputCarTokenType.getVin().equals(inputCarTokenType.getVin()));
            require.using("Make cannot be updated.",
                    outputCarTokenType.getMake().equals(inputCarTokenType.getMake()));
            require.using("Mileage cannot be decreased.",
                    outputCarTokenType.getMileage() >= inputCarTokenType.getMileage());
            require.using("Price cannot be 0.",
                    outputCarTokenType.getPrice() > 0L);
            return null;
        });
    }
}
