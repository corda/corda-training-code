package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
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

import java.util.Collections;

public interface IssueCarToHolderFlows {

    /**
     * This flow is to be started from the car dealer.
     * This flow can be started from the command-line with:
     * flow start IssueCarToHolderFlows$IssueCarToHolderSimpleFlow carTypeId: abc-..., holder: PartyA
     */
    @StartableByRPC
    class IssueCarToHolderSimpleFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final UniqueIdentifier carTypeId;
        @NotNull
        private final AbstractParty holder;

        public IssueCarToHolderSimpleFlow(
                @NotNull final UniqueIdentifier carTypeId,
                @NotNull final AbstractParty holder) {
            //noinspection ConstantConditions
            if (carTypeId == null) throw new NullPointerException("The carTypeId cannot be null");
            //noinspection ConstantConditions
            if (holder == null) throw new NullPointerException("The holder cannot be null");
            this.carTypeId = carTypeId;
            this.holder = holder;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final TokenPointer<CarTokenType> carPointer = new TokenPointer<>(
                    new LinearPointer<>(carTypeId, CarTokenType.class, false),
                    0);
            return subFlow(new IssueCarToHolderFlow(carPointer, getOurIdentity(), holder));
        }
    }

    @StartableByRPC
    class IssueCarToHolderFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final TokenPointer<CarTokenType> carPointer;
        @NotNull
        private final Party dealership;
        @NotNull
        private final AbstractParty holder;

        public IssueCarToHolderFlow(
                @NotNull final TokenPointer<CarTokenType> carPointer,
                @NotNull final Party dealership,
                @NotNull final AbstractParty holder) {
            //noinspection ConstantConditions
            if (carPointer == null) throw new NullPointerException("The car cannot be null");
            //noinspection ConstantConditions
            if (dealership == null) throw new NullPointerException("The dealership cannot be null");
            //noinspection ConstantConditions
            if (holder == null) throw new NullPointerException("The holder cannot be null");
            this.carPointer = carPointer;
            this.dealership = dealership;
            this.holder = holder;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final IssuedTokenType bmwWithDealership = new IssuedTokenType(dealership, carPointer);
            final NonFungibleToken heldCar = new NonFungibleToken(
                    bmwWithDealership, holder,
                    new UniqueIdentifier(), null);
            return subFlow(new IssueTokens(Collections.singletonList(heldCar)));
        }
    }

}
