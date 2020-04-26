package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.template.car.state.CarTokenType;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class IssueCarTokenTypeFlow extends FlowLogic<SignedTransaction> {

    @NotNull
    private final Party notary;
    @NotNull
    private final String vin;
    @NotNull
    private final String make;
    private final long price;
    @NotNull
    private final List<Party> observers;

    public IssueCarTokenTypeFlow(
            @NotNull final Party notary, @NotNull final String vin, @NotNull final String make,
            final long price, @NotNull List<Party> observers) {
        this.notary = notary;
        this.vin = vin;
        this.make = make;
        this.price = price;
        this.observers = observers;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        final Party dmv = getOurIdentity();
        if (!dmv.getName().equals(CarTokenTypeConstants.DMV)) {
            throw new FlowException("We are not the DMV");
        }
        final CarTokenType newCar = new CarTokenType(Collections.singletonList(dmv),
                new UniqueIdentifier(), vin, make, 0, price);
        final TransactionState<CarTokenType> txState = new TransactionState<>(newCar, notary);
        return subFlow(new CreateEvolvableTokens(txState, observers));
    }
}
