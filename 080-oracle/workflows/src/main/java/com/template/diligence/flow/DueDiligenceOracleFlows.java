package com.template.diligence.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.template.diligence.state.DiligenceOracleUtilities;
import com.template.diligence.state.DueDiligence;
import com.template.diligence.state.DueDiligenceContract.Commands;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TimeWindow;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.*;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.template.diligence.state.DiligenceOracleUtilities.VALID_DURATION;

public interface DueDiligenceOracleFlows {

    interface Query {

        @InitiatingFlow
        class Request extends FlowLogic<DiligenceOracleUtilities.Status> {

            @NotNull
            private final AbstractParty oracle;
            @NotNull
            private final UniqueIdentifier tokenId;

            public Request(
                    @NotNull final AbstractParty oracle,
                    @NotNull final UniqueIdentifier tokenId) {
                //noinspection ConstantConditions
                if (oracle == null) throw new NullPointerException("oracle cannot be null");
                //noinspection ConstantConditions
                if (tokenId == null) throw new NullPointerException("tokenId cannot be null");
                this.oracle = oracle;
                this.tokenId = tokenId;
            }

            @NotNull
            @Suspendable
            @Override
            public DiligenceOracleUtilities.Status call() throws FlowException {
                final Party oracleHost = getServiceHub().getIdentityService()
                        .requireWellKnownPartyFromAnonymous(oracle);
                return initiateFlow(oracleHost).sendAndReceive(DiligenceOracleUtilities.Status.class, tokenId)
                        .unwrap(it -> it);
            }
        }

        @SuppressWarnings("unused")
        @InitiatedBy(Request.class)
        class Answer extends FlowLogic<DiligenceOracleUtilities.Status> {

            @NotNull
            private final FlowSession requesterSession;

            public Answer(@NotNull final FlowSession requesterSession) {
                //noinspection ConstantConditions
                if (requesterSession == null) throw new NullPointerException("requesterSession cannot be null");
                this.requesterSession = requesterSession;
            }

            @NotNull
            @Suspendable
            @Override
            public DiligenceOracleUtilities.Status call() throws FlowException {
                final DiligenceOracleUtilities.Status status = getServiceHub().cordaService(DiligenceOracle.class)
                        .query(requesterSession.receive(UniqueIdentifier.class).unwrap(it -> it));
                requesterSession.send(status);
                return status;
            }
        }

    }

    interface Prepare {

        /**
         * Handler is {@link PrepareHandlerFlow}.
         */
        @InitiatingFlow
        class PrepareFlow extends FlowLogic<StateAndRef<DueDiligence>> {

            private final static ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on parameters.");
            private final static ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
            private final static ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private keys.");
            private final static ProgressTracker.Step RESOLVING_PARTICIPANTS = new ProgressTracker.Step("Resolving the participants' hosts.");
            public final static ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
                @Override
                public ProgressTracker childProgressTracker() {
                    return CollectSignaturesFlow.Companion.tracker();
                }
            };
            private final static ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                @Override
                public ProgressTracker childProgressTracker() {
                    return FinalityFlow.Companion.tracker();
                }
            };

            @NotNull
            public static ProgressTracker tracker() {
                return new ProgressTracker(
                        GENERATING_TRANSACTION,
                        VERIFYING_TRANSACTION,
                        SIGNING_TRANSACTION,
                        RESOLVING_PARTICIPANTS,
                        GATHERING_SIGS,
                        FINALISING_TRANSACTION);
            }

            @NotNull
            private final List<AbstractParty> participants;
            @NotNull
            private final UniqueIdentifier tokenId;
            @NotNull
            private final Party notary;
            @NotNull
            private final AbstractParty oracle;
            @NotNull
            private final ProgressTracker progressTracker;

            public PrepareFlow(
                    @NotNull final List<AbstractParty> participants,
                    @NotNull final UniqueIdentifier tokenId,
                    @NotNull final Party notary,
                    @NotNull final AbstractParty oracle,
                    @NotNull final ProgressTracker progressTracker) {
                //noinspection ConstantConditions
                if (participants == null) throw new NullPointerException("participants cannot be null");
                //noinspection ConstantConditions
                if (tokenId == null) throw new NullPointerException("tokenId cannot be null");
                //noinspection ConstantConditions
                if (notary == null) throw new NullPointerException("notary cannot be null");
                //noinspection ConstantConditions
                if (oracle == null) throw new NullPointerException("oracle cannot be null");
                //noinspection ConstantConditions
                if (progressTracker == null) throw new NullPointerException("progressTracker cannot be null");
                this.participants = participants;
                this.tokenId = tokenId;
                this.notary = notary;
                this.oracle = oracle;
                this.progressTracker = progressTracker;
            }

            public PrepareFlow(
                    @NotNull final List<AbstractParty> participants,
                    @NotNull final UniqueIdentifier tokenId,
                    @NotNull final Party notary,
                    @NotNull final AbstractParty oracle) {
                this(participants, tokenId, notary, oracle, tracker());
            }

            @Suspendable
            @NotNull
            @Override
            public StateAndRef<DueDiligence> call() throws FlowException {
                progressTracker.setCurrentStep(GENERATING_TRANSACTION);
                final TransactionBuilder builder = new TransactionBuilder(notary)
                        .addOutputState(new DueDiligence(new UniqueIdentifier(),
                                tokenId, oracle, participants))
                        .addCommand(new Commands.Prepare(),
                                participants.stream()
                                        .map(AbstractParty::getOwningKey)
                                        .collect(Collectors.toList()));

                progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
                builder.verify(getServiceHub());

                progressTracker.setCurrentStep(SIGNING_TRANSACTION);
                final List<PublicKey> myKeys = participants.stream()
                        .filter(it -> getServiceHub().getIdentityService()
                                .requireWellKnownPartyFromAnonymous(it).equals(getOurIdentity()))
                        .distinct()
                        .map(AbstractParty::getOwningKey)
                        .collect(Collectors.toList());
                final SignedTransaction partSigned = getServiceHub().signInitialTransaction(builder, myKeys);

                progressTracker.setCurrentStep(RESOLVING_PARTICIPANTS);
                final List<Party> otherParticipants = participants.stream()
                        .map(it -> getServiceHub().getIdentityService().requireWellKnownPartyFromAnonymous(it))
                        .filter(it -> !getOurIdentity().equals(it))
                        .distinct()
                        .collect(Collectors.toList());

                final ArrayList<FlowSession> sessions = new ArrayList<>(otherParticipants.size());
                otherParticipants.forEach(it -> sessions.add(initiateFlow(it)));

                progressTracker.setCurrentStep(GATHERING_SIGS);
                final SignedTransaction signed = subFlow(new CollectSignaturesFlow(
                        partSigned, sessions, myKeys, GATHERING_SIGS.childProgressTracker()));

                progressTracker.setCurrentStep(FINALISING_TRANSACTION);
                return subFlow(new FinalityFlow(signed, sessions, FINALISING_TRANSACTION.childProgressTracker()))
                        .getCoreTransaction().outRef(0);
            }
        }

        @InitiatedBy(PrepareFlow.class)
        class PrepareHandlerFlow extends FlowLogic<StateAndRef<DueDiligence>> {
            @NotNull
            private final FlowSession requesterSession;

            public PrepareHandlerFlow(@NotNull final FlowSession requesterSession) {
                //noinspection ConstantConditions
                if (requesterSession == null) throw new NullPointerException("requesterSession cannot be null");
                this.requesterSession = requesterSession;
            }

            @Suspendable
            @NotNull
            @Override
            public StateAndRef<DueDiligence> call() throws FlowException {
                final SecureHash txId = subFlow(new SignTransactionFlow(requesterSession) {
                    @Override
                    protected void checkTransaction(@NotNull final SignedTransaction stx) throws FlowException {
                        // Tx looks legit?
                        if (!stx.getInputs().isEmpty())
                            throw new FlowException("Unexpected inputs");
                        if (stx.getCoreTransaction().getOutputs().size() != 1)
                            throw new FlowException("Expected a single output");
                        if (stx.getCoreTransaction().outputsOfType(DueDiligence.class).size() != 1)
                            throw new FlowException("The only output should be a DueDiligence");
                        // Do I own this token?
                        final DueDiligence output = (DueDiligence) stx.getCoreTransaction().outRef(0)
                                .getState().getData();
                        final QueryCriteria criteria = new QueryCriteria.LinearStateQueryCriteria()
                                .withUuid(Collections.singletonList(output.getTokenId().getId()));
                        final List<StateAndRef<NonFungibleToken>> found = getServiceHub().getVaultService()
                                .queryBy(NonFungibleToken.class, criteria).getStates();
                        if (found.size() != 1)
                            throw new FlowException("Unknown or too many such tokenId");
                        final AbstractParty holder = found.get(0).getState().getData().getHolder();
                        final boolean myKey = getServiceHub().getKeyManagementService()
                                .filterMyKeys(Collections.singletonList(holder.getOwningKey()))
                                .iterator()
                                .hasNext();
                        if (!myKey) throw new FlowException("The underlying NonFungibleToken is not held by me");
                    }
                }).getId();
                return subFlow(new ReceiveFinalityFlow(requesterSession, txId))
                        .getCoreTransaction()
                        .outRef(0);
            }
        }

    }

    interface Drop {

        /**
         * Handler is {@link DropHandlerFlow}.
         */
        @InitiatingFlow
        class DropFlow extends FlowLogic<SignedTransaction> {

            private final static ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on parameters.");
            private final static ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
            private final static ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private keys.");
            private final static ProgressTracker.Step RESOLVING_PARTICIPANTS = new ProgressTracker.Step("Resolving the participants' hosts.");
            public final static ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
                @Override
                public ProgressTracker childProgressTracker() {
                    return CollectSignaturesFlow.Companion.tracker();
                }
            };
            private final static ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                @Override
                public ProgressTracker childProgressTracker() {
                    return FinalityFlow.Companion.tracker();
                }
            };

            @NotNull
            public static ProgressTracker tracker() {
                return new ProgressTracker(
                        GENERATING_TRANSACTION,
                        VERIFYING_TRANSACTION,
                        SIGNING_TRANSACTION,
                        RESOLVING_PARTICIPANTS,
                        GATHERING_SIGS,
                        FINALISING_TRANSACTION);
            }

            @NotNull
            private final StateAndRef<DueDiligence> dueDiligence;
            @NotNull
            private final ProgressTracker progressTracker;

            public DropFlow(
                    @NotNull final StateAndRef<DueDiligence> dueDiligence,
                    @NotNull final ProgressTracker progressTracker) {
                //noinspection ConstantConditions
                if (dueDiligence == null) throw new NullPointerException("dueDiligence cannot be null");
                //noinspection ConstantConditions
                if (progressTracker == null) throw new NullPointerException("progressTracker cannot be null");
                this.dueDiligence = dueDiligence;
                this.progressTracker = progressTracker;
            }

            public DropFlow(
                    @NotNull final StateAndRef<DueDiligence> dueDiligence) {
                this(dueDiligence, tracker());
            }

            @Suspendable
            @NotNull
            @Override
            public SignedTransaction call() throws FlowException {
                progressTracker.setCurrentStep(GENERATING_TRANSACTION);
                final List<AbstractParty> participants = dueDiligence.getState().getData().getParticipants();
                final TransactionBuilder builder = new TransactionBuilder(dueDiligence.getState().getNotary())
                        .addInputState(dueDiligence)
                        .addCommand(new Commands.Drop(),
                                participants.stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));

                progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
                builder.verify(getServiceHub());

                progressTracker.setCurrentStep(SIGNING_TRANSACTION);
                final List<PublicKey> myKeys = participants.stream()
                        .filter(it -> getServiceHub().getIdentityService()
                                .requireWellKnownPartyFromAnonymous(it).equals(getOurIdentity()))
                        .filter(it -> getOurIdentity().equals(it))
                        .distinct()
                        .map(AbstractParty::getOwningKey)
                        .collect(Collectors.toList());
                final SignedTransaction partSigned = getServiceHub().signInitialTransaction(builder, myKeys);

                progressTracker.setCurrentStep(RESOLVING_PARTICIPANTS);
                final List<Party> otherParticipants = participants.stream()
                        .map(it -> getServiceHub().getIdentityService().requireWellKnownPartyFromAnonymous(it))
                        .filter(it -> !getOurIdentity().equals(it))
                        .distinct()
                        .collect(Collectors.toList());

                final ArrayList<FlowSession> sessions = new ArrayList<>(otherParticipants.size());
                otherParticipants.forEach(it -> sessions.add(initiateFlow(it)));

                progressTracker.setCurrentStep(GATHERING_SIGS);
                final SignedTransaction signed = subFlow(new CollectSignaturesFlow(
                        partSigned, sessions, myKeys, GATHERING_SIGS.childProgressTracker()));

                progressTracker.setCurrentStep(FINALISING_TRANSACTION);
                return subFlow(new FinalityFlow(signed, sessions, FINALISING_TRANSACTION.childProgressTracker()));
            }
        }

        @InitiatedBy(DropFlow.class)
        class DropHandlerFlow extends FlowLogic<SignedTransaction> {

            @NotNull
            private final FlowSession requesterSession;

            public DropHandlerFlow(@NotNull final FlowSession requesterSession) {
                //noinspection ConstantConditions
                if (requesterSession == null) throw new NullPointerException("requesterSession cannot be null");
                this.requesterSession = requesterSession;
            }

            @Suspendable
            @NotNull
            @Override
            public SignedTransaction call() throws FlowException {
                final SecureHash txId = subFlow(new SignTransactionFlow(requesterSession) {
                    @Override
                    protected void checkTransaction(@NotNull final SignedTransaction stx) throws FlowException {
                        // Tx looks legit?
                        if (stx.getInputs().size() != 1)
                            throw new FlowException("Expected a single input");
                        if (!stx.getCoreTransaction().getOutputs().isEmpty())
                            throw new FlowException("Unexpected outputs");
                        final StateAndRef<ContractState> input = getServiceHub().toStateAndRef(stx.getInputs().get(0));
                        if (!(input.getState().getData() instanceof DueDiligence))
                            throw new FlowException("The only input should be a DueDiligence");
                        // Am I a participant?
                        final List<PublicKey> participantKeys = input.getState().getData().getParticipants()
                                .stream().map(AbstractParty::getOwningKey)
                                .collect(Collectors.toList());
                        final boolean myKey = getServiceHub().getKeyManagementService()
                                .filterMyKeys(participantKeys)
                                .iterator()
                                .hasNext();
                        if (!myKey) throw new FlowException("There is no signature required by me");
                    }
                }).getId();
                return subFlow(new ReceiveFinalityFlow(requesterSession, txId));
            }
        }

    }

    interface Certify {

        /**
         * Its handler is {@link FinaliseStraight}.
         */
        @InitiatingFlow
        class RequestStraight extends FlowLogic<SignedTransaction> {

            private final static ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on parameters.");
            private final static ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
            private final static ProgressTracker.Step REQUESTING_ORACLE = new ProgressTracker.Step("Requestion signature from the oracle.");
            private final static ProgressTracker.Step RESOLVING_PARTICIPANTS = new ProgressTracker.Step("Resolving the participants' hosts.");
            private final static ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                @Override
                public ProgressTracker childProgressTracker() {
                    return FinalityFlow.Companion.tracker();
                }
            };

            @NotNull
            public static ProgressTracker tracker() {
                return new ProgressTracker(
                        GENERATING_TRANSACTION,
                        VERIFYING_TRANSACTION,
                        REQUESTING_ORACLE,
                        RESOLVING_PARTICIPANTS,
                        FINALISING_TRANSACTION);
            }

            @NotNull
            private final StateAndRef<DueDiligence> dueDilRef;
            @NotNull
            private final DiligenceOracleUtilities.Status status;
            @NotNull
            private final ProgressTracker progressTracker;

            public RequestStraight(
                    @NotNull final StateAndRef<DueDiligence> dueDilRef,
                    @NotNull final DiligenceOracleUtilities.Status status,
                    @NotNull final ProgressTracker progressTracker) {
                //noinspection ConstantConditions
                if (dueDilRef == null) throw new NullPointerException("dueDilRef cannot be null");
                //noinspection ConstantConditions
                if (status == null) throw new NullPointerException("status cannot be null");
                //noinspection ConstantConditions
                if (progressTracker == null) throw new NullPointerException("progressTracker cannot be null");
                this.dueDilRef = dueDilRef;
                this.status = status;
                this.progressTracker = progressTracker;
            }

            public RequestStraight(
                    @NotNull final StateAndRef<DueDiligence> dueDilRef,
                    @NotNull final DiligenceOracleUtilities.Status status) {
                this(dueDilRef, status, tracker());
            }

            @Suspendable
            @NotNull
            @Override
            public SignedTransaction call() throws FlowException {
                progressTracker.setCurrentStep(GENERATING_TRANSACTION);
                final DueDiligence dueDil = dueDilRef.getState().getData();
                final TransactionBuilder builder = new TransactionBuilder(dueDilRef.getState().getNotary())
                        .addInputState(dueDilRef)
                        .addCommand(new Commands.Certify(dueDil.getTokenId(), status),
                                dueDil.getOracle().getOwningKey())
                        .setTimeWindow(TimeWindow.untilOnly(
                                Instant.now().plus(VALID_DURATION).minus(Duration.ofSeconds(1))));

                progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
                builder.verify(getServiceHub());

                progressTracker.setCurrentStep(REQUESTING_ORACLE);
                final WireTransaction wtx = builder.toWireTransaction(getServiceHub());
                final TransactionSignature oracleSig = subFlow(new Request(dueDil.getOracle(), wtx));
                final SignedTransaction signed = new SignedTransaction(wtx, Collections.singletonList(oracleSig));

                progressTracker.setCurrentStep(RESOLVING_PARTICIPANTS);
                final List<Party> otherParticipants = new ArrayList<>(dueDil.getParticipants()).stream()
                        .map(it -> getServiceHub().getIdentityService().requireWellKnownPartyFromAnonymous(it))
                        .filter(it -> !getOurIdentity().equals(it))
                        .distinct()
                        .collect(Collectors.toList());

                final ArrayList<FlowSession> sessions = new ArrayList<>(otherParticipants.size());
                otherParticipants.forEach(it -> sessions.add(initiateFlow(it)));

                progressTracker.setCurrentStep(FINALISING_TRANSACTION);
                return subFlow(new FinalityFlow(signed, sessions));
            }
        }

        @SuppressWarnings("unused")
        @InitiatedBy(RequestStraight.class)
        class FinaliseStraight extends FlowLogic<SignedTransaction> {

            @NotNull
            private final FlowSession requesterSession;

            public FinaliseStraight(@NotNull final FlowSession requesterSession) {
                //noinspection ConstantConditions
                if (requesterSession == null) throw new NullPointerException("requesterSession cannot be null");
                this.requesterSession = requesterSession;
            }

            @Suspendable
            @NotNull
            @Override
            public SignedTransaction call() throws FlowException {
                return subFlow(new ReceiveFinalityFlow(requesterSession));
            }
        }

        /**
         * Its handler is {@link Answer}
         */
        @InitiatingFlow
        class Request extends FlowLogic<TransactionSignature> {

            @NotNull
            private final AbstractParty oracle;
            @NotNull
            private final WireTransaction tx;

            public Request(
                    @NotNull final AbstractParty oracle,
                    @NotNull final WireTransaction tx) {
                //noinspection ConstantConditions
                if (oracle == null) throw new NullPointerException("oracle cannot be null");
                //noinspection ConstantConditions
                if (tx == null) throw new NullPointerException("tx cannot be null");
                this.oracle = oracle;
                this.tx = tx;
            }

            @Suspendable
            @NotNull
            @Override
            public TransactionSignature call() throws FlowException {
                final Party oracleHost = getServiceHub().getIdentityService().requireWellKnownPartyFromAnonymous(oracle);
                return initiateFlow(oracleHost)
                        .sendAndReceive(TransactionSignature.class, DiligenceOracleUtilities.filter(tx, oracle))
                        .unwrap(sig -> {
                            if (sig.getBy().equals(oracle.getOwningKey())) {
                                tx.checkSignature(sig);
                                return sig;
                            }
                            throw new IllegalArgumentException("Unexpected key used for signature");
                        });
            }
        }

        @InitiatedBy(Request.class)
        class Answer extends FlowLogic<TransactionSignature> {

            @NotNull
            private final FlowSession requesterSession;

            public Answer(@NotNull final FlowSession requesterSession) {
                //noinspection ConstantConditions
                if (requesterSession == null) throw new NullPointerException("requesterSession cannot be null");
                this.requesterSession = requesterSession;
            }

            @NotNull
            @Suspendable
            @Override
            public TransactionSignature call() throws FlowException {
                final FilteredTransaction received = requesterSession.receive(FilteredTransaction.class)
                        .unwrap(it -> it);
                final TransactionSignature sig;
                try {
                    sig = getServiceHub().cordaService(DiligenceOracle.class)
                            .sign(received);
                } catch (Exception e) {
                    throw new FlowException(e);
                }
                requesterSession.send(sig);
                return sig;
            }
        }

    }

}
