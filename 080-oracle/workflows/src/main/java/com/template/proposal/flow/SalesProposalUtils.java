package com.template.proposal.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.selection.TokenQueryBy;
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection;
import com.template.proposal.state.SalesProposal;
import kotlin.Pair;
import kotlin.jvm.functions.Function1;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.node.services.vault.QueryCriteria;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.r3.corda.lib.tokens.selection.database.config.DatabaseSelectionConfigKt.*;

public class SalesProposalUtils {

    @NotNull
    private final FlowLogic<?> flow;

    public SalesProposalUtils(@NotNull final FlowLogic<?> flow) {
        this.flow = flow;
    }

    @NotNull
    public StateAndRef<SalesProposal> findBy(@NotNull final UUID uuid) throws FlowException {
        final QueryCriteria proposalCriteria = new QueryCriteria.LinearStateQueryCriteria()
                .withUuid(Collections.singletonList(uuid));
        final List<StateAndRef<SalesProposal>> proposals = flow.getServiceHub().getVaultService()
                .queryBy(SalesProposal.class, proposalCriteria)
                .getStates();
        if (proposals.size() != 1) throw new FlowException("Wrong number of proposals found");
        return proposals.get(0);
    }

    @Suspendable
    @NotNull
    public Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> generateMove(
            @NotNull final SalesProposal proposal,
            @NotNull final QueryCriteria heldByBuyer) {
        final IssuedTokenType issuedCurrency = proposal.getPrice().getToken();
        final Amount<TokenType> priceInCurrency = new Amount<>(
                proposal.getPrice().getQuantity(),
                proposal.getPrice().getToken());
        // Generate the buyer's currency inputs, to be spent, and the outputs, the currency tokens that will be
        // held by the seller.
        final DatabaseTokenSelection tokenSelection = new DatabaseTokenSelection(
                flow.getServiceHub(), MAX_RETRIES_DEFAULT, RETRY_SLEEP_DEFAULT, RETRY_CAP_DEFAULT, PAGE_SIZE_DEFAULT);
        return tokenSelection.generateMove(
                // Eventually held by the seller.
                Collections.singletonList(new Pair<>(proposal.getSeller(), priceInCurrency)),
                // We see here that we should not rely on the default value, because the buyer keeps the change.
                proposal.getBuyer(),
                new TokenQueryBy(
                        issuedCurrency.getIssuer(),
                        (Function1<? super StateAndRef<? extends FungibleToken>, Boolean> & Serializable) it -> true,
                        heldByBuyer),
                flow.getRunId().getUuid());
    }

}
