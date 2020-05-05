package com.template.proposal.service;

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.template.proposal.flow.InformTokenBuyerFlows;
import com.template.proposal.state.SalesProposal;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.node.AppServiceHub;
import net.corda.core.node.services.CordaService;
import net.corda.core.node.services.Vault;
import net.corda.core.serialization.SingletonSerializeAsToken;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This service informs potential buyers of SalesProposals when the underlying token type has changed.
 */
@CordaService
public class SalesProposalService extends SingletonSerializeAsToken {

    private static final int THREAD_COUNT = 4;
    private final static Logger log = LoggerFactory.getLogger(SalesProposalService.class);
    private final static Executor executor = Executors.newFixedThreadPool(THREAD_COUNT);

    @NotNull
    private final AppServiceHub serviceHub;
    /**
     * Map key: The EvolvableTokenType.
     * Map value: The potential buyer.
     */
    @NotNull
    private final Map<StateAndRef<? extends EvolvableTokenType>, List<AbstractParty>> trackedTypesToBuyers;

    @SuppressWarnings("unused")
    public SalesProposalService(@NotNull final AppServiceHub serviceHub) {
        this.serviceHub = serviceHub;
        this.trackedTypesToBuyers = new HashMap<>();
        trackAndNotify();
    }

    private void trackAndNotify() {
        // We track before we collect the current stuff.
        // It would be more pleasant to have 2 trackBy, but this does not work as they compete for the db connection.
        serviceHub.getVaultService().trackBy(ContractState.class).getUpdates().subscribe(
                this::handleUpdate,
                error -> log.error("In ContractState tracking", error),
                () -> log.info("ContractState updates closed!"));
        serviceHub.getVaultService().queryBy(SalesProposal.class).getStates()
                .forEach(it -> putProposal(it.getState().getData()));
    }

    private void handleUpdate(@NotNull final Vault.Update<ContractState> update) {
        // For EvolvableTokenType, we care only about the "net" result. For the same id, there may be more than 1 consumed,
        // but if there is any consumed, then there is a single produced.
        // Map key: EvolvableTokenType linear id
        // Map value: Tracked and consumed EvolvableTokenType
        final Map<UniqueIdentifier, StateAndRef<EvolvableTokenType>> toNotify = new HashMap<>();
        // We need to look at consumed first to build the pair in the map.
        update.getConsumed().forEach(it -> {
            if (it.getState().getData() instanceof SalesProposal) {
                removeProposal((SalesProposal) it.getState().getData());
            } else if (it.getState().getData() instanceof EvolvableTokenType) {
                final StateAndRef<EvolvableTokenType> consumed = convertToType(it);
                if (trackedTypesToBuyers.get(consumed) != null) {
                    final UniqueIdentifier id = consumed.getState().getData().getLinearId();
                    assert toNotify.get(id) == null; // Because it should be the first time we see it.
                    // We will need to notify
                    toNotify.put(id, consumed);
                }
            }
        });
        // Clean the outdated information if a consumed SalesProposal removed a tracking.
        toNotify.forEach((id, state) -> {
            if (trackedTypesToBuyers.get(state) == null) toNotify.remove(id);
        });
        update.getProduced().forEach(it -> {
            if (it.getState().getData() instanceof SalesProposal) {
                putProposal((SalesProposal) it.getState().getData());
            } else if (it.getState().getData() instanceof EvolvableTokenType) {
                final StateAndRef<EvolvableTokenType> produced = convertToType(it);
                final UniqueIdentifier id = produced.getState().getData().getLinearId();
                final StateAndRef<EvolvableTokenType> consumed = toNotify.get(id);
                if (consumed != null) {
                    trackedTypesToBuyers.put(produced, trackedTypesToBuyers.get(consumed));
                    notifyUpdate(consumed, produced);
                    toNotify.remove(id);
                }
            }
        });
        // The remaining ones have exited the ledger for good. At the moment, this is impossible.
        toNotify.forEach((id, state) -> trackedTypesToBuyers.remove(state));
    }

    @NotNull
    public StateAndRef<EvolvableTokenType> convertToType(@NotNull final StateAndRef<ContractState> state) {
        return new StateAndRef<>(
                new TransactionState<>(
                        (EvolvableTokenType) state.getState().getData(),
                        state.getState().getContract(),
                        state.getState().getNotary(),
                        state.getState().getEncumbrance(),
                        state.getState().getConstraint()),
                state.getRef());
    }

    /**
     * @param who The key to test.
     * @return Whether this key belongs to this AppServiceHub.
     */
    public boolean isMyKey(@NotNull final AbstractParty who) {
        return serviceHub.getKeyManagementService()
                .filterMyKeys(Collections.singletonList(who.getOwningKey()))
                .iterator()
                .hasNext();
    }

    @Nullable
    public StateAndRef<EvolvableTokenType> getTokenType(@NotNull final SalesProposal proposal) {
        final TokenType type = proposal.getAsset().getState().getData().getTokenType();
        if (!type.isPointer()) return null;
        //noinspection unchecked
        return ((TokenPointer<EvolvableTokenType>) type)
                .getPointer()
                .resolve(serviceHub);
    }

    private void putProposal(@NotNull final SalesProposal proposal) {
        // If we are not the seller, we do not need to watch.
        if (!isMyKey(proposal.getSeller())) return;
        final StateAndRef<EvolvableTokenType> tokenType = getTokenType(proposal);
        // If it is not evolvable, there is nothing to track.
        if (tokenType == null) return;
        final List<AbstractParty> buyers = trackedTypesToBuyers.get(tokenType);
        if (buyers == null) {
            trackedTypesToBuyers.put(tokenType, new ArrayList<>(Collections.singletonList(proposal.getBuyer())));
        } else {
            buyers.add(proposal.getBuyer());
        }
    }

    private void removeProposal(@NotNull final SalesProposal proposal) {
        final StateAndRef<EvolvableTokenType> tokenType = getTokenType(proposal);
        // If it is not evolvable, nothing was tracked in the first place.
        if (tokenType == null) return;
        trackedTypesToBuyers.remove(tokenType);
    }

    private void notifyUpdate(
            @NotNull final StateAndRef<EvolvableTokenType> consumed,
            @NotNull final StateAndRef<EvolvableTokenType> replacement) {
        final UniqueIdentifier stateId = consumed.getState().getData().getLinearId();
        final List<AbstractParty> buyers = trackedTypesToBuyers.get(consumed);
        final SignedTransaction tx = serviceHub.getValidatedTransactions().getTransaction(
                replacement.getRef().getTxhash());
        assert buyers != null;
        assert tx != null; // Should never happen.
        for (final AbstractParty buyer : buyers)
            executor.execute(() ->
                    serviceHub.startTrackedFlow(new InformTokenBuyerFlows.Send(buyer, tx))
                            .getProgress()
                            .subscribe(
                                    result -> log.info("Notified buyer " + buyer + " of change of " + stateId +
                                            "with result " + result),
                                    e -> log.error("Failed to notify buyer " + buyer + " of change of " + stateId, e),
                                    () -> trackedTypesToBuyers.remove(consumed)
                            ));
    }

    public int getTokenTypeCount() {
        return trackedTypesToBuyers.size();
    }

    @Nullable
    public List<AbstractParty> getBuyersOf(@NotNull final StateAndRef<? extends EvolvableTokenType> tokenType) {
        return trackedTypesToBuyers.get(tokenType);
    }

}
