package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken;
import com.template.car.state.CarTokenType;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@StartableByRPC
public class UpdateCarTokenTypeFlow extends FlowLogic<SignedTransaction> {

    @NotNull
    private final StateAndRef<CarTokenType> carRef;
    private final long mileage;
    private final long price;
    @NotNull
    private final List<Party> observers;

    public UpdateCarTokenTypeFlow(
            @NotNull final StateAndRef<CarTokenType> carRef,
            final long mileage, final long price,
            final @NotNull List<Party> observers) {
        this.carRef = carRef;
        this.mileage = mileage;
        this.price = price;
        this.observers = observers;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        final CarTokenType car = carRef.getState().getData();
        final CarTokenType updatedCar = new CarTokenType(car.getMaintainers(), car.getLinearId(),
                car.getVin(), car.getMake(), mileage, price);
        return subFlow(new UpdateEvolvableToken(carRef, updatedCar, observers));
    }
}
