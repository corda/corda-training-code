package com.template.contracts;

import com.template.states.TokenState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class TokenContract implements Contract {
    public static final String TOKEN_CONTRACT_ID = "com.template.contracts.TokenContract";

    @Override
    public void verify(@NotNull final LedgerTransaction tx) {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);

        // This contract does not care about states it has no knowledge about.
        // This will be useful, for instance, when the token is exchanged in a trade.
        final List<TokenState> inputs = tx.inputsOfType(TokenState.class);
        final List<TokenState> outputs = tx.outputsOfType(TokenState.class);

        if (command.getValue() instanceof Commands.Mint) {
            requireThat(req -> {
                // Constraints on the shape of the transaction.
                req.using("No tokens should be consumed when minting.", inputs.isEmpty());
                req.using("There should be minted tokens.", !outputs.isEmpty());

                // Constraints on the minted tokens themselves.
                // The "above 0" constraint is enforced at the constructor level.

                // Constraints on the signers.
                req.using("The issuers should sign.",
                        command.getSigners().containsAll(outputs.stream()
                                .map(it -> it.getIssuer().getOwningKey())
                                .distinct()
                                .collect(Collectors.toList())
                        ));
                // We assume the owners need not sign although they are participants.

                return null;
            });
        } else {
            throw new IllegalArgumentException("Unknown command " + command.getValue());
        }
    }

    public interface Commands extends CommandData {
        class Mint implements Commands {
        }
    }
}