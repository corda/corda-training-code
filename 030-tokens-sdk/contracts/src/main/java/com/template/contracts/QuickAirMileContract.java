package com.template.contracts;

import com.template.states.QuickAirMileToken;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.finance.contracts.asset.OnLedgerAsset;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * For demonstration purposes only.
 */
public class QuickAirMileContract extends OnLedgerAsset<
        QuickAirMileToken.AirMile,
        QuickAirMileContract.Commands,
        QuickAirMileToken> {

    @NotNull
    @Override
    public TransactionState<QuickAirMileToken> deriveState(
            @NotNull final TransactionState<? extends QuickAirMileToken> txState,
            @NotNull final Amount<Issued<QuickAirMileToken.AirMile>> amount,
            @NotNull final AbstractParty owner) {
        return new TransactionState<>(
                txState.getData().withNewOwnerAndAmount(amount, owner),
                txState.getContract(),
                txState.getNotary(),
                txState.getEncumbrance(),
                txState.getConstraint());
    }

    @NotNull
    @Override
    public Collection<CommandWithParties<Commands>> extractCommands(
            @NotNull final Collection<? extends CommandWithParties<? extends CommandData>> commands) {
        //noinspection unchecked
        return commands.stream()
                .filter(it -> it.getValue() instanceof Commands)
                .map(it -> (CommandWithParties<Commands>) it)
                .collect(Collectors.toList());
    }

    @NotNull
    @Override
    public CommandData generateExitCommand(@NotNull Amount<Issued<QuickAirMileToken.AirMile>> amount) {
        return new Commands.Redeem();
    }

    @NotNull
    @Override
    public MoveCommand generateMoveCommand() {
        return new Commands.Move();
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        throw new NotImplementedException("That's a lot of work");
    }

    public interface Commands extends CommandData {
        @SuppressWarnings("unused")
        class Issue implements Commands {
        }

        class Move implements Commands, MoveCommand {
            @Nullable
            @Override
            public Class<? extends Contract> getContract() {
                return QuickAirMileContract.class;
            }
        }

        class Redeem implements Commands {
        }
    }
}
