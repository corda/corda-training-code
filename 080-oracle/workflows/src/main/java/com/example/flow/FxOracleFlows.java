package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.oracle.FxOracle;
import com.example.oracle.FxOracleUtilities;
import com.example.oracle.FxQuote;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import javafx.util.Pair;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.*;
import org.jetbrains.annotations.NotNull;

public interface FxOracleFlows {

    interface Query {

        /**
         * Its handler is {@link Answer}.
         */
        @InitiatingFlow
        @StartableByRPC
        class Request extends FlowLogic<FxQuote> {
            @NotNull
            private final TokenType base;
            @NotNull
            private final TokenType counter;
            @NotNull
            private final Party oracle;

            public Request(
                    @NotNull final TokenType base,
                    @NotNull final TokenType counter,
                    @NotNull final Party oracle) {
                //noinspection ConstantConditions
                if (base == null) throw new NullPointerException("base cannot be null");
                //noinspection ConstantConditions
                if (counter == null) throw new NullPointerException("counter cannot be null");
                //noinspection ConstantConditions
                if (oracle == null) throw new NullPointerException("oracle cannot be null");
                this.base = base;
                this.counter = counter;
                this.oracle = oracle;
            }

            @Suspendable
            @Override
            public FxQuote call() throws FlowException {
                return initiateFlow(oracle)
                        .sendAndReceive(FxQuote.class, new Pair<>(base, counter))
                        .unwrap(it -> it);
            }
        }

        @SuppressWarnings("unused")
        @InitiatedBy(Request.class)
        class Answer extends FlowLogic<FxQuote> {
            @NotNull
            private final FlowSession requesterSession;

            public Answer(@NotNull final FlowSession requesterSession) {
                //noinspection ConstantConditions
                if (requesterSession == null) throw new NullPointerException("requesterSession cannot be null");
                this.requesterSession = requesterSession;
            }

            @Suspendable
            @Override
            public FxQuote call() throws FlowException {
                //noinspection unchecked
                final FxQuote quote = getServiceHub().cordaService(FxOracle.class)
                        .getQuote(requesterSession.receive(Pair.class).unwrap(it -> it));
                if (quote == null) throw new FlowException("Unavailable pair");
                requesterSession.send(quote);
                return quote;
            }
        }

    }

    interface Sign {

        /**
         * Its handler is {@link Answer}.
         */
        @InitiatingFlow
        @StartableByRPC
        class Request extends FlowLogic<TransactionSignature> {
            @NotNull
            private final TransactionBuilder txBuilder;
            @NotNull
            private final Party oracle;
            @NotNull
            private final SignedTransaction tx;

            public Request(
                    @NotNull final TransactionBuilder txBuilder,
                    @NotNull final Party oracle,
                    @NotNull final SignedTransaction tx) {
                //noinspection ConstantConditions
                if (txBuilder == null) throw new NullPointerException("txBuilder cannot be null");
                //noinspection ConstantConditions
                if (oracle == null) throw new NullPointerException("oracle cannot be null");
                //noinspection ConstantConditions
                if (tx == null) throw new NullPointerException("tx cannot be null");
                this.txBuilder = txBuilder;
                this.oracle = oracle;
                this.tx = tx;
            }

            @Suspendable
            @Override
            public TransactionSignature call() throws FlowException {
                return initiateFlow(oracle)
                        .sendAndReceive(TransactionSignature.class, FxOracleUtilities.filter(tx, oracle))
                        .unwrap(sig -> {
                            if (sig.getBy().equals(oracle.getOwningKey())) {
                                txBuilder.toWireTransaction(getServiceHub()).checkSignature(sig);
                                return sig;
                            }
                            throw new IllegalArgumentException("Unexpected key used for signature");
                        });
            }
        }

        @SuppressWarnings("unused")
        @InitiatedBy(Request.class)
        class Answer extends FlowLogic<TransactionSignature> {
            @NotNull
            private final FlowSession requesterSession;

            public Answer(@NotNull final FlowSession requesterSession) {
                //noinspection ConstantConditions
                if (requesterSession == null) throw new NullPointerException("requester cannot be null");
                this.requesterSession = requesterSession;
            }

            @Suspendable
            @Override
            public TransactionSignature call() throws FlowException {
                final FilteredTransaction partial = requesterSession.receive(FilteredTransaction.class)
                        .unwrap(it -> it);
                final TransactionSignature sig;
                try {
                    sig = getServiceHub().cordaService(FxOracle.class)
                            .sign(partial);
                } catch (FilteredTransactionVerificationException | ComponentVisibilityException e) {
                    throw new FlowException(e);
                }
                requesterSession.send(sig);
                return sig;
            }
        }

    }

}
