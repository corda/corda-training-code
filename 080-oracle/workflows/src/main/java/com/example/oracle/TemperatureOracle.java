package com.example.oracle;

import com.example.contract.TemperatureContract.Commands.HowWarm;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.TimeWindow;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.node.AppServiceHub;
import net.corda.core.node.services.CordaService;
import net.corda.core.serialization.SingletonSerializeAsToken;
import net.corda.core.transactions.ComponentVisibilityException;
import net.corda.core.transactions.FilteredTransaction;
import net.corda.core.transactions.FilteredTransactionVerificationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

@CordaService
public class TemperatureOracle extends SingletonSerializeAsToken {

    public static final Duration MAX_VALIDITY = Duration.ofMinutes(5);

    private final AppServiceHub serviceHub;
    private final PublicKey oracleKey;
    private final Random tempGenerator;
    private BigDecimal currentTemp;

    @SuppressWarnings("unused")
    public TemperatureOracle(@NotNull final AppServiceHub serviceHub) {
        //noinspection ConstantConditions
        if (serviceHub == null) throw new NullPointerException("serviceHub cannot be null");
        this.serviceHub = serviceHub;
        oracleKey = serviceHub.getMyInfo().getLegalIdentities().get(0).getOwningKey();
        tempGenerator = new Random(Instant.now().getNano());
        currentTemp = BigDecimal.TEN;
    }

    /**
     * Called when the oracle is requested to provide the current temperature.
     */
    @NotNull
    public BigDecimal getCurrentTemperature() {
        final BigDecimal nextChange = BigDecimal.valueOf(tempGenerator.nextInt(1000))
                .divide(BigDecimal.valueOf(1000), RoundingMode.HALF_EVEN);
        currentTemp = currentTemp.add(nextChange);
        return currentTemp;
    }

    /**
     * Called when the oracle is requested to sign over current temperature.
     */
    @NotNull
    public TransactionSignature sign(FilteredTransaction ftx) throws FilteredTransactionVerificationException,
            ComponentVisibilityException {
        // Check that the partial Merkle tree is valid.
        ftx.verify();

        // Is it a valid Merkle tree that the oracle is willing to sign over?
        if (!ftx.checkWithFun(this::isCommandWithCorrectTemp))
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
    private boolean isCommandWithCorrectTemp(@NotNull final Object elem) {
        //noinspection rawtypes
        if (elem instanceof Command && ((Command) elem).getValue() instanceof HowWarm) {
            //noinspection rawtypes
            final HowWarm cmdData = (HowWarm) ((Command) elem).getValue();
            // Is the temperature within the bounds, inclusive?
            final BigDecimal current = getCurrentTemperature();
            final int lowBound = cmdData.getLowBound().compareTo(current);
            final int highBound = current.compareTo(cmdData.getHighBound());
            // Check that the oracle is a required signer.
            //noinspection rawtypes
            return ((Command) elem).getSigners().contains(oracleKey)
                    && lowBound <= 0 && highBound <= 0;
        } else if (elem instanceof TimeWindow) {
            final Instant untilTime = ((TimeWindow) elem).getUntilTime();
            return untilTime != null
                    && untilTime.isBefore(Instant.now());
        }
        // We don't want to jinx checkCommandVisibility.
        return false;
    }

}