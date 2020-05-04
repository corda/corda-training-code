package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveNonFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken;
import com.template.car.state.CarTokenType;
import net.corda.core.contracts.LinearPointer;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

interface MoveCarToNewHolderFlows {

    /**
     * This flow can be started from the command line with:
     * flow start MoveCarToNewHolderFlows$MoveCarToNewHolderSimpleFlow carTypeId: abc-.., newHolder: PartyB, observers: [ DMV, Dealer ]
     */
    @StartableByRPC
    class MoveCarToNewHolderSimpleFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final UniqueIdentifier carTypeId;
        @NotNull
        private final AbstractParty newHolder;
        @NotNull
        private final List<Party> observers;

        public MoveCarToNewHolderSimpleFlow(
                @NotNull final UniqueIdentifier carTypeId,
                @NotNull final AbstractParty newHolder,
                @NotNull final List<Party> observers) {
            //noinspection ConstantConditions
            if (carTypeId == null) throw new NullPointerException("The carTypeId cannot be null");
            //noinspection ConstantConditions
            if (newHolder == null) throw new NullPointerException("The newHolder cannot be null");
            //noinspection ConstantConditions
            if (observers == null) throw new NullPointerException("The observers cannot be null");
            this.carTypeId = carTypeId;
            this.newHolder = newHolder;
            this.observers = observers;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final TokenPointer<CarTokenType> carPointer = new TokenPointer<>(
                    new LinearPointer<>(carTypeId, CarTokenType.class, false),
                    0);
            return subFlow(new MoveCarToNewHolderFlow(carPointer, newHolder, observers));
        }
    }

    @StartableByRPC
    class MoveCarToNewHolderFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final TokenPointer<CarTokenType> carTokenTypePointer;
        @NotNull
        private final AbstractParty newHolder;
        @NotNull
        private final List<Party> observers;

        public MoveCarToNewHolderFlow(
                @NotNull TokenPointer<CarTokenType> carTokenTypePointer,
                @NotNull AbstractParty newHolder,
                @NotNull List<Party> observers) {
            //noinspection ConstantConditions
            if (carTokenTypePointer == null) throw new NullPointerException("The carTokenTypePointer cannot be null");
            //noinspection ConstantConditions
            if (newHolder == null) throw new NullPointerException("The newHolder cannot be null");
            //noinspection ConstantConditions
            if (observers == null) throw new NullPointerException("The observers cannot be null");
            this.carTokenTypePointer = carTokenTypePointer;
            this.newHolder = newHolder;
            this.observers = observers;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final PartyAndToken newHolderAndCar = new PartyAndToken(newHolder, carTokenTypePointer);
            return subFlow(new MoveNonFungibleTokens(newHolderAndCar, observers));
        }
    }

}