package com.template.proposal.service;

import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.template.car.state.CarTokenType;
import com.template.proposal.flow.InformCarBuyerFlows;
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

    private static final int THREAD_COUNT = 8;
    private final static Logger log = LoggerFactory.getLogger(SalesProposalService.class);
    private final static Executor executor = Executors.newFixedThreadPool(THREAD_COUNT);

    @NotNull
    private final AppServiceHub serviceHub;
    /**
     * Map key: The CarTokenType.
     * Map value: The potential buyer.
     */
    @NotNull
    private final Map<StateAndRef<CarTokenType>, List<AbstractParty>> trackedCarsToBuyers;

    @SuppressWarnings("unused")
    public SalesProposalService(@NotNull final AppServiceHub serviceHub) {
        this.serviceHub = serviceHub;
        this.trackedCarsToBuyers = new HashMap<>();
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
        // For CarTokenType, we care only about the "net" result. For the same id, there may be more than 1 consumed,
        // but if there is any consumed, then there is a single produced.
        // Map key: CarTokenType linear id
        // Map value: Tracked and consumed CarTokenType
        final Map<UniqueIdentifier, StateAndRef<CarTokenType>> toNotify = new HashMap<>();
        // We need to look at consumed first to build the pair in the map.
        update.getConsumed().forEach(it -> {
            if (it.getState().getData() instanceof SalesProposal) {
                removeProposal((SalesProposal) it.getState().getData());
            } else if (it.getState().getData() instanceof CarTokenType) {
                final StateAndRef<CarTokenType> consumed = convertToCar(it);
                if (trackedCarsToBuyers.get(consumed) != null) {
                    final UniqueIdentifier id = consumed.getState().getData().getLinearId();
                    assert toNotify.get(id) == null; // Because it should be the first time we see it.
                    // We will need to notify
                    toNotify.put(id, consumed);
                }
            }
        });
        update.getProduced().forEach(it -> {
            if (it.getState().getData() instanceof SalesProposal) {
                putProposal((SalesProposal) it.getState().getData());
            } else if (it.getState().getData() instanceof CarTokenType) {
                final StateAndRef<CarTokenType> produced = convertToCar(it);
                final StateAndRef<CarTokenType> consumed = toNotify.get(produced.getState().getData().getLinearId());
                if (consumed != null) {
                    notifyUpdate(consumed, produced);
                }
            }
        });
    }

    @NotNull
    public StateAndRef<CarTokenType> convertToCar(@NotNull final StateAndRef<ContractState> state) {
        return new StateAndRef<>(
                new TransactionState<>(
                        (CarTokenType) state.getState().getData(),
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

    @NotNull
    public StateAndRef<CarTokenType> getCarType(@NotNull final SalesProposal proposal) {
        //noinspection unchecked
        return ((TokenPointer<CarTokenType>) proposal.getAsset()
                .getState().getData()
                .getTokenType())
                .getPointer()
                .resolve(serviceHub);
    }

    private void putProposal(@NotNull final SalesProposal proposal) {
        // If we are not the seller, we do not need to watch.
        if (!isMyKey(proposal.getSeller())) {
            return;
        }
        final StateAndRef<CarTokenType> carType = getCarType(proposal);
        if (trackedCarsToBuyers.get(carType) == null) {
            trackedCarsToBuyers.put(carType, new ArrayList<>(Collections.singletonList(proposal.getBuyer())));
        } else {
            trackedCarsToBuyers.get(carType).add(proposal.getBuyer());
        }
    }

    private void removeProposal(@NotNull final SalesProposal proposal) {
        trackedCarsToBuyers.remove(getCarType(proposal));
    }

    private void notifyUpdate(
            @NotNull final StateAndRef<CarTokenType> consumed,
            @NotNull final StateAndRef<CarTokenType> replacement) {
        final UniqueIdentifier carId = consumed.getState().getData().getLinearId();
        final List<AbstractParty> buyers = trackedCarsToBuyers.get(consumed);
        assert buyers != null;
        trackedCarsToBuyers.put(replacement, buyers);
        final SignedTransaction tx = serviceHub.getValidatedTransactions().getTransaction(
                replacement.getRef().getTxhash());
        assert tx != null; // Should never happen.
        executor.execute(() -> {
            for (final AbstractParty buyer : buyers)
                serviceHub.startTrackedFlow(new InformCarBuyerFlows.Send(buyer, tx))
                        .getProgress()
                        .subscribe(
                                result -> log.info("Notified buyer " + buyer + " of change of " + carId +
                                        "with result " + result),
                                e -> log.error("Failed to notify buyer " + buyer + " of change of " + carId, e),
                                () -> trackedCarsToBuyers.remove(consumed)
                        );
        });
    }

    public int getCarTypeCount() {
        return trackedCarsToBuyers.size();
    }

    @Nullable
    public List<AbstractParty> getBuyersOf(@NotNull final StateAndRef<CarTokenType> carType) {
        return trackedCarsToBuyers.get(carType);
    }

}
