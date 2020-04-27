package com.template.dummy.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler;
import com.template.dummy.state.DummyContract;
import com.template.dummy.state.DummyState;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface DummyFlow {
    @InitiatingFlow
    public class Create extends FlowLogic<SignedTransaction> {
        @NotNull
        private final List<DummyState> toCreate;
        @NotNull
        private final List<Party> observers;

        public Create(@NotNull final List<DummyState> toCreate, @NotNull final List<Party> observers) {
            this.toCreate = toCreate;
            this.observers = observers;
        }

        public Create(@NotNull final List<DummyState> toCreate) {
            this(toCreate, Collections.emptyList());
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final TransactionBuilder txBuilder = new TransactionBuilder(getServiceHub().getNetworkMapCache()
                    .getNotaryIdentities().get(0));
            toCreate.forEach(txBuilder::addOutputState);
            final List<AbstractParty> parties = toCreate.stream()
                    .flatMap(dummyState -> Stream.of(dummyState.getLost(), dummyState.getSeen()))
                    .distinct()
                    .collect(Collectors.toList());
            final List<PublicKey> signingKeys = parties.stream()
                    .map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList());
            txBuilder.addCommand(
                    new DummyContract.Commands.Create(),
                    signingKeys);
            final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder, signingKeys);
            final List<FlowSession> observerSessions = observers.stream()
                    .map(this::initiateFlow)
                    .collect(Collectors.toList());
            for (FlowSession observerSession : observerSessions) {
                subFlow(new SyncKeyMappingFlow(observerSession, parties));
            }
            return subFlow(new FinalityFlow(signedTx, observerSessions));
        }
    }

    @InitiatedBy(Create.class)
    public class Receive extends FlowLogic<SignedTransaction> {
        @NotNull
        private final FlowSession counterpartySession;

        public Receive(@NotNull final FlowSession counterpartySession) {
            this.counterpartySession = counterpartySession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            subFlow(new SyncKeyMappingFlowHandler(counterpartySession));
            return subFlow(new ReceiveFinalityFlow(counterpartySession, null, StatesToRecord.ALL_VISIBLE));
        }
    }
}