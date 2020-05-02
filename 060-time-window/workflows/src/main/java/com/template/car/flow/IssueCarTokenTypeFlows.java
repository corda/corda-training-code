package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.template.car.state.CarTokenType;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public interface IssueCarTokenTypeFlows {

    /**
     * This flow is to be started from the maintainer for the CarTokenType: e.g. the DMV.
     * This flow can be started from the command line with:
     * flow start IssueCarTokenTypeFlows$IssueCarTokenTypeFlow notary: Notary, vin: abc, make: bmw, price: 600, observers: [ Dealer ]
     */
    @StartableByRPC
    class IssueCarTokenTypeFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final Party notary;
        @NotNull
        private final String vin;
        @NotNull
        private final String make;
        @NotNull
        private final List<Party> observers;

        public IssueCarTokenTypeFlow(
                @NotNull final Party notary,
                @NotNull final String vin,
                @NotNull final String make,
                @NotNull final List<Party> observers) {
            //noinspection ConstantConditions
            if (notary == null) throw new NullPointerException("The notary cannot be null");
            //noinspection ConstantConditions
            if (vin == null) throw new NullPointerException("The vin cannot be null");
            //noinspection ConstantConditions
            if (make == null) throw new NullPointerException("The make cannot be null");
            //noinspection ConstantConditions
            if (observers == null) throw new NullPointerException("The observers cannot be null");
            this.notary = notary;
            this.vin = vin;
            this.make = make;
            this.observers = observers;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final CarTokenType newCar = new CarTokenType(Collections.singletonList(getOurIdentity()),
                    new UniqueIdentifier(), vin, make, 0);
            final TransactionState<CarTokenType> txState = new TransactionState<>(newCar, notary);
            return subFlow(new CreateEvolvableTokens(txState, observers));
        }
    }

}