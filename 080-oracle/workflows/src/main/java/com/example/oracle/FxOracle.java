package com.example.oracle;

import com.example.contract.FxContract.Commands.Swap;
import com.example.oracle.FxQuote;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import javafx.util.Pair;
import net.corda.core.contracts.Command;
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
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

@CordaService
public class FxOracle extends SingletonSerializeAsToken {

    public static final Duration VALID_DURATION = Duration.ofSeconds(20);
    public static final int POP_COUNT = 2;

    private final AppServiceHub serviceHub;
    private final PublicKey oracleKey;
    // For simplicity, the oracle is storing the FX rates in a map.
    private final Map<Pair<TokenType, TokenType>, BigDecimal> rates = new HashMap<>(10);
    private final Map<FxQuote, Boolean> sentQuotesMap = new HashMap<>(10);
    private final LinkedList<FxQuote> sentQuotes = new LinkedList<>();

    @SuppressWarnings("unused")
    public FxOracle(@NotNull final AppServiceHub serviceHub) {
        //noinspection ConstantConditions
        if (serviceHub == null) throw new NullPointerException("serviceHub cannot be null");
        this.serviceHub = serviceHub;
        oracleKey = serviceHub.getMyInfo().getLegalIdentities().get(0).getOwningKey();
        loadRates();
    }

    /**
     * Called when the oracle is requested to provide an FX quote.
     */
    @Nullable
    public FxQuote getQuote(@NotNull final TokenType base, @NotNull final TokenType counter) {
        return getQuote(new Pair<>(base, counter));
    }

    @Nullable
    public FxQuote getQuote(@NotNull final Pair<TokenType,TokenType> pair) {
        /*
         * This simplified example assumes that the oracle has all of the possible FX rates stored in a map.
         * In a real-world scenario, this would probably be an API call.
         */
        final FxQuote quote = new FxQuote(
                pair.getKey(),
                pair.getValue(),
                rates.get(pair),
                Instant.now().plus(VALID_DURATION));
        popOldQuotes(POP_COUNT);
        sentQuotes.addLast(quote);
        sentQuotesMap.put(quote, true);
        return quote;
    }

    /**
     * Called when the oracle is requested to sign over a FX rate.
     */
    @NotNull
    public TransactionSignature sign(FilteredTransaction ftx) throws FilteredTransactionVerificationException,
            ComponentVisibilityException {
        // Check that the partial Merkle tree is valid.
        ftx.verify();

        // Is it a valid Merkle tree that the oracle is willing to sign over?
        if (!ftx.checkWithFun(this::isCommandWithCorrectFXRate))
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
    private boolean isCommandWithCorrectFXRate(@NotNull final Object elem) {
        //noinspection rawtypes
        if (elem instanceof Command && ((Command) elem).getValue() instanceof Swap) {
            //noinspection rawtypes
            final Swap cmdData = (Swap) ((Command) elem).getValue();
            final FxQuote quote = cmdData.getQuote();
            final Boolean isMyQuote = sentQuotesMap.get(quote);
            // Check that the oracle is a required signer.
            //noinspection rawtypes
            return ((Command) elem).getSigners().contains(oracleKey)
                    // Check that the enclosed quote was indeed provided by the oracle.
                    && isMyQuote != null
                    && isMyQuote;
        }
        // We don't want to jinx checkCommandVisibility.
        return false;
    }

    /**
     * On-going cleanup.
     */
    private void popOldQuotes(@SuppressWarnings("SameParameterValue") int countLeft) {
        while (0 < countLeft) {
            final FxQuote old = sentQuotes.peekFirst();
            if (old != null && old.getExpirationDate().isBefore(Instant.now())) {
                sentQuotes.removeFirst();
                sentQuotesMap.remove(old);
                countLeft--;
            } else {
                countLeft = 0;
            }
        }

    }

    // Dummy data.
    private void loadRates() {
        this.rates.put(createPair("USD", "CAD"), BigDecimal.valueOf(1.39));
        this.rates.put(createPair("CAD", "USD"), BigDecimal.valueOf(0.72));
    }

    @NotNull
    private Pair<TokenType, TokenType> createPair(
            @NotNull final String base, @NotNull final String counter) {
        return new Pair<>(FiatCurrency.Companion.getInstance(base),
                FiatCurrency.Companion.getInstance(counter));
    }
}