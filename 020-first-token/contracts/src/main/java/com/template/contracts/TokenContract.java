package com.template.contracts;

import com.template.states.TokenState;
import com.template.states.TokenStateUtilities;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
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

        if (command.getValue() instanceof Commands.Issue) {
            requireThat(req -> {
                // Constraints on the shape of the transaction.
                req.using("No tokens should be consumed when issuing.", inputs.isEmpty());
                req.using("There should be issued tokens.", !outputs.isEmpty());

                // Constraints on the issued tokens themselves.
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
        } else if (command.getValue() instanceof Commands.Move) {
            requireThat(req -> {
                // Constraints on the shape of the transaction.
                req.using("There should be tokens to move.", !inputs.isEmpty());
                req.using("There should be moved tokens.", !outputs.isEmpty());

                // Constraints on the redeemed tokens themselves.
                // The "above 0" constraint is enforced at the constructor level.
                final Map<Party, Long> inputSums = TokenStateUtilities.mapSumByIssuer(inputs);
                final Map<Party, Long> outputSums = TokenStateUtilities.mapSumByIssuer(outputs);
                req.using(
                        "Consumed and created issuers should be identical.",
                        inputSums.keySet().equals(outputSums.keySet()));
                req.using(
                        "The sum of quantities for each issuer should be conserved.",
                        inputSums.entrySet().stream()
                                .allMatch(entry -> outputSums.get(entry.getKey()).equals(entry.getValue())));

                // Constraints on the signers.
                req.using("The current holders should sign.",
                        command.getSigners().containsAll(inputs.stream()
                                .map(it -> it.getHolder().getOwningKey())
                                .distinct()
                                .collect(Collectors.toList())
                        ));

                return null;
            });
        } else if (command.getValue() instanceof Commands.Redeem) {
            requireThat(req -> {
                // Constraints on the shape of the transaction.
                req.using("There should be tokens to redeem.", !inputs.isEmpty());
                req.using("No tokens should be issued when redeeming.", outputs.isEmpty());

                // Constraints on the redeemed tokens themselves.
                // The "above 0" constraint is enforced at the constructor level.

                // Constraints on the signers.
                req.using("The issuers should sign.",
                        command.getSigners().containsAll(inputs.stream()
                                .map(it -> it.getIssuer().getOwningKey())
                                .distinct()
                                .collect(Collectors.toList())
                        ));
                req.using("The current holders should sign.",
                        command.getSigners().containsAll(inputs.stream()
                                .map(it -> it.getHolder().getOwningKey())
                                .distinct()
                                .collect(Collectors.toList())
                        ));

                return null;
            });
        } else {
            throw new IllegalArgumentException("Unknown command " + command.getValue());
        }
    }

    public interface Commands extends CommandData {
        class Issue implements Commands {
        }

        class Move implements Commands {
        }

        class Redeem implements Commands {
        }
    }
}