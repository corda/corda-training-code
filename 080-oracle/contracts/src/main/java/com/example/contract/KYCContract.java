package com.example.contract;

import com.example.state.KYCState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class KYCContract implements Contract {
    @Override
    public void verify(@NotNull final LedgerTransaction tx) throws IllegalArgumentException {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        if (command.getValue() instanceof Commands.Create) {
            requireThat(require -> {
                // Generic constraints around the KYC transaction.
                require.using("No KYC inputs should be consumed when issuing a KYC.",
                        tx.inputsOfType(KYCState.class).isEmpty());
                final List<KYCState> outList = tx.outputsOfType(KYCState.class);
                require.using("Only one output state should be created.",
                        outList.size() == 1);
                final KYCState state = outList.get(0);

                // Signatures constraints.
                require.using("The issuer must be the signer.",
                        command.getSigners().size() == 1 &&
                        command.getSigners().contains(state.getIssuer().getOwningKey()));

                // No state-specific constraints.
                return null;
            });
        } else {
            throw new IllegalArgumentException("Unknown command " + command.getValue());
        }
    }

    public interface Commands extends CommandData {
        class Create implements Commands {}
        // TODO update and revoke
    }}
