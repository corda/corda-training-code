package com.template.diligence.flow;

import com.template.diligence.state.DiligenceOracleUtilities;
import com.template.diligence.state.DiligenceOracleUtilities.Status;
import com.template.diligence.state.DueDiligenceContract.Commands.Certify;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.TimeWindow;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.node.AppServiceHub;
import net.corda.core.node.services.CordaService;
import net.corda.core.serialization.SingletonSerializeAsToken;
import net.corda.core.transactions.ComponentVisibilityException;
import net.corda.core.transactions.FilteredTransaction;
import net.corda.core.transactions.FilteredTransactionVerificationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.template.diligence.state.DiligenceOracleUtilities.VALID_DURATION;

@CordaService
public class DiligenceOracle extends SingletonSerializeAsToken {

    public static final String ACCOUNT_NAME = "DiligenceOracle";

    @NotNull
    private final AppServiceHub serviceHub;
    @Nullable
    private  PublicKey oracleKey;
    // For simplicity, the oracle is storing the statuses in a map.
    private final Map<UniqueIdentifier, Status> freeAndClears = new HashMap<>(10);

    @SuppressWarnings("unused")
    public DiligenceOracle(@NotNull final AppServiceHub serviceHub)
            throws Exception {
        //noinspection ConstantConditions
        if (serviceHub == null) throw new NullPointerException("serviceHub cannot be null");
        this.serviceHub = serviceHub;
//        final CordaFuture<PublicKey> future = serviceHub.startFlow(new DiligenceOracleFlows.ObtainKey(ACCOUNT_NAME))
//                .getReturnValue();
//        this.oracleKey = future.get();
    }

    /**
     * Called when the oracle is requested to provide a "free and clear" status.
     */
    @NotNull
    public Status query(@NotNull final UniqueIdentifier tokenId) {
        final Status status = freeAndClears.get(tokenId);
        return status == null ? Status.Clear : status;
    }

    /**
     * Called when the oracle is requested to sign over a status.
     */
    @NotNull
    public TransactionSignature sign(@NotNull final FilteredTransaction ftx)
            throws FilteredTransactionVerificationException,
            ComponentVisibilityException {
        if (oracleKey == null) throw new NullPointerException("oracleKey not initialised");
        // Check that the partial Merkle tree is valid.
        ftx.verify();

        // Is it a valid Merkle tree that the oracle is willing to sign over?
        if (!ftx.checkWithFun(this::isCommandWithCorrectParameters))
            throw new IllegalArgumentException("Oracle signature requested over an invalid transaction.");

        // Check that the sender of the transaction didn't filter out other commands that require
        // the oracle's signature. I.e. don't be stolen.
        ftx.checkCommandVisibility(oracleKey);

        // Sign the transaction.
        return serviceHub.createSignature(ftx, oracleKey);
    }

    /**
     * Passed the visible elements found in the filtered transaction.
     */
    private boolean isCommandWithCorrectParameters(@NotNull final Object elem) {
        //noinspection rawtypes
        if (elem instanceof Command && ((Command) elem).getValue() instanceof Certify) {
            //noinspection rawtypes
            final Certify cmdData = (Certify) ((Command) elem).getValue();
            // Check that the oracle is a required signer.
            //noinspection rawtypes
            return ((Command) elem).getSigners().contains(oracleKey)
                    // Certify that it is of the right status.
                    && cmdData.getStatus().equals(query(cmdData.getTokenId()));
        } else if (elem instanceof TimeWindow) {
            final Instant untilTime = ((TimeWindow) elem).getUntilTime();
            // This is valid for only so long.
            return untilTime != null && untilTime.isBefore(Instant.now().plus(VALID_DURATION));
        }
        // We don't want to jinx checkCommandVisibility.
        return false;
    }

    public void setStatus(@NotNull final UniqueIdentifier tokenId, @NotNull final Status status) {
        if (status == Status.Clear) freeAndClears.remove(tokenId);
        else freeAndClears.put(tokenId, status);
    }

    protected void setOracleKey(@NotNull final PublicKey oracleKey) {
        //noinspection ConstantConditions
        if (oracleKey == null) throw new NullPointerException("oracleKey cannot be null");
        this.oracleKey = oracleKey;
    }

    @Nullable
    public PublicKey getOracleKey() {
        return oracleKey;
    }

}