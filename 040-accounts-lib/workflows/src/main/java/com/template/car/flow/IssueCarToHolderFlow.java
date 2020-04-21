package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.template.car.state.CarTokenType;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class IssueCarToHolderFlow extends FlowLogic<SignedTransaction> {

    @NotNull
    private final CarTokenType car;
    @NotNull
    private final Party dealership;
    @NotNull
    private final AbstractParty holder;

    public IssueCarToHolderFlow(
            @NotNull final CarTokenType car, @NotNull final Party dealership, @NotNull final AbstractParty holder) {
        this.car = car;
        this.dealership = dealership;
        this.holder = holder;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        final TokenPointer<CarTokenType> bmwPointer = car.toPointer(CarTokenType.class);
        final IssuedTokenType bmwWithDealership = new IssuedTokenType(dealership, bmwPointer);
        final NonFungibleToken heldCar = new NonFungibleToken(
                bmwWithDealership, holder,
                new UniqueIdentifier(), null);
        return subFlow(new IssueTokens(Collections.singletonList(heldCar)));
    }
}
