package com.template.diligence.flow;

import com.template.diligence.state.DueDiligence;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.node.services.vault.QueryCriteria;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DueDiligenceFlowUtils {

    @NotNull
    private final FlowLogic<?> flow;

    public DueDiligenceFlowUtils(@NotNull final FlowLogic<?> flow) {
        this.flow = flow;
    }

    @NotNull
    public StateAndRef<DueDiligence> findBy(@NotNull final UUID uuid) throws FlowException {
        final QueryCriteria dueDilCriteria = new QueryCriteria.LinearStateQueryCriteria()
                .withUuid(Collections.singletonList(uuid));
        final List<StateAndRef<DueDiligence>> dueDils = flow.getServiceHub().getVaultService()
                .queryBy(DueDiligence.class, dueDilCriteria)
                .getStates();
        if (dueDils.size() != 1) throw new FlowException("Wrong number of DueDiligence found");
        return dueDils.get(0);
    }

}
