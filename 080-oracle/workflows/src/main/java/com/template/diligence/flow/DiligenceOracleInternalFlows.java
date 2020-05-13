package com.template.diligence.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.template.diligence.state.DiligenceOracleUtilities.Status;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;

public interface DiligenceOracleInternalFlows {

    class SetOracleKeyFlow extends FlowLogic<Void> {

        @NotNull
        private final PublicKey oracleKey;

        public SetOracleKeyFlow(@NotNull final PublicKey oracleKey) {
            //noinspection ConstantConditions
            if (oracleKey == null) throw new NullPointerException("oracleKey cannot be null");
            this.oracleKey = oracleKey;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            getServiceHub().cordaService(DiligenceOracle.class).setOracleKey(oracleKey);
            return null;
        }
    }

    class SetStatus extends FlowLogic<Void> {

        @NotNull
        private final UniqueIdentifier tokenId;
        @NotNull
        private final Status status;

        public SetStatus(
                @NotNull UniqueIdentifier tokenId,
                @NotNull Status status) {
            //noinspection ConstantConditions
            if (tokenId == null) throw new NullPointerException("tokenId cannot be null");
            //noinspection ConstantConditions
            if (status == null) throw new NullPointerException("status cannot be null");
            this.tokenId = tokenId;
            this.status = status;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            getServiceHub().cordaService(DiligenceOracle.class).setStatus(tokenId, status);
            return null;
        }
    }

}
