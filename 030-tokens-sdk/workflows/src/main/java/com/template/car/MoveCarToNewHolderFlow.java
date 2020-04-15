package com.template.car;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveNonFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MoveCarToNewHolderFlow extends FlowLogic<SignedTransaction> {

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
