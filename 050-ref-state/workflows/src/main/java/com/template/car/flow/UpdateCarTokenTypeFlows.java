package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken;
import com.template.car.state.CarTokenType;
import net.corda.core.contracts.LinearPointer;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface UpdateCarTokenTypeFlows {

    /**
     * This flow can be started on the command-line with:
     * flow start UpdateCarTokenTypeFlows$UpdateCarTokenTypeSimpleFlow carTypeId: abc-1..., mileage: 20000, price: 400, observers: [ PartyA ]
     */
    @StartableByRPC
    class UpdateCarTokenTypeSimpleFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final UniqueIdentifier carTypeId;
        private final long mileage;
        private final long price;
        @NotNull
        private final List<Party> observers;

        public UpdateCarTokenTypeSimpleFlow(
                @NotNull final UniqueIdentifier carTypeId,
                final long mileage,
                final long price,
                @NotNull final List<Party> observers) {
            //noinspection ConstantConditions
            if (carTypeId == null) throw new NullPointerException("The carTypeId cannot be null");
            //noinspection ConstantConditions
            if (observers == null) throw new NullPointerException("The observers cannot be null");
            this.carTypeId = carTypeId;
            this.mileage = mileage;
            this.price = price;
            this.observers = observers;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final TokenPointer<CarTokenType> carPointer = new TokenPointer<>(
                    new LinearPointer<>(carTypeId, CarTokenType.class, false),
                    0);
            final StateAndRef<CarTokenType> carRef = carPointer.getPointer().resolve(getServiceHub());
            return subFlow(new UpdateCarTokenTypeFlow(carRef, mileage, price, observers));
        }
    }

    @StartableByRPC
    class UpdateCarTokenTypeFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final StateAndRef<CarTokenType> carRef;
        private final long mileage;
        private final long price;
        @NotNull
        private final List<Party> observers;

        public UpdateCarTokenTypeFlow(
                @NotNull final StateAndRef<CarTokenType> carRef,
                final long mileage,
                final long price,
                @NotNull final List<Party> observers) {
            //noinspection ConstantConditions
            if (carRef == null) throw new NullPointerException("The carRef cannot be null");
            //noinspection ConstantConditions
            if (observers == null) throw new NullPointerException("The observers cannot be null");
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

}