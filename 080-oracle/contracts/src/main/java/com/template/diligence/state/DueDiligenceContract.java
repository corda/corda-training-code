package com.template.diligence.state;

import com.template.diligence.state.DiligenceOracleUtilities.Status;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class DueDiligenceContract implements Contract {

    public static final String DUE_DILIGENCE_CONTRACT_ID = "com.template.diligence.state.DueDiligenceContract";

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final List<StateAndRef<DueDiligence>> inDueDil = tx.inRefsOfType(DueDiligence.class);
        final List<StateAndRef<DueDiligence>> outDueDil = tx.outRefsOfType(DueDiligence.class);

        requireThat(req -> {

            if (command.getValue() instanceof Commands.Prepare) {
                req.using("There should be no due diligence inputs on prepare",
                        inDueDil.isEmpty());
                req.using("There should be a single due diligence output on prepare",
                        outDueDil.size() == 1);

                final DueDiligence diligence = outDueDil.get(0).getState().getData();
                req.using("The participants should be the only signers on prepare",
                        diligence.getParticipants().stream()
                                .map(AbstractParty::getOwningKey)
                                .collect(Collectors.toList())
                                .equals(command.getSigners()));

            } else if (command.getValue() instanceof Commands.Certify) {
                req.using("There should be a single due diligence input on certify",
                        inDueDil.size() == 1);
                req.using("There should be no due diligence outputs on certify",
                        outDueDil.isEmpty());

                final DueDiligence diligence = inDueDil.get(0).getState().getData();
                req.using("The command id should match that of the input",
                        ((Commands.Certify) command.getValue()).tokenId.equals(diligence.getTokenId()));

                req.using("The oracle should be the only signer on certify",
                        Collections.singletonList(diligence.getOracle().getOwningKey()).equals(command.getSigners()));

            } else if (command.getValue() instanceof Commands.Drop) {
                req.using("There should be a single due diligence input on drop",
                        inDueDil.size() == 1);
                req.using("There should be no due diligence outputs on drop",
                        outDueDil.isEmpty());

                final DueDiligence diligence = inDueDil.get(0).getState().getData();
                req.using("The participants should be the only signers on drop",
                        diligence.getParticipants().stream()
                                .map(AbstractParty::getOwningKey)
                                .collect(Collectors.toList())
                                .equals(command.getSigners()));

            } else {
                throw new IllegalArgumentException("Unknown command: " + command.getValue());
            }

            return null;
        });
    }

    public interface Commands extends CommandData {
        class Prepare implements Commands {
        }

        class Certify implements Commands {
            @NotNull
            private final UniqueIdentifier tokenId;
            @NotNull
            private final Status status;

            public Certify(
                    @NotNull final UniqueIdentifier tokenId,
                    @NotNull final Status status) {
                //noinspection ConstantConditions
                if (tokenId == null) throw new NullPointerException("tokenId cannot be null");
                //noinspection ConstantConditions
                if (status == null) throw new NullPointerException("status cannot be null");
                this.tokenId = tokenId;
                this.status = status;
            }

            @NotNull
            public UniqueIdentifier getTokenId() {
                return tokenId;
            }

            @NotNull
            public Status getStatus() {
                return status;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                final Certify certify = (Certify) o;
                return tokenId.equals(certify.tokenId)
                        && status.equals(certify.status);
            }

            @Override
            public int hashCode() {
                return Objects.hash(tokenId, status);
            }
        }

        class Drop implements Commands {
        }
    }
}
