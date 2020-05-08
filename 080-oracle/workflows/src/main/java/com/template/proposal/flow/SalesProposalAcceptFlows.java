package com.template.proposal.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow;
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import com.template.diligence.flow.DueDiligenceFlowUtils;
import com.template.diligence.flow.DueDiligenceOracleFlows;
import com.template.diligence.state.DiligenceOracleUtilities;
import com.template.diligence.state.DueDiligence;
import com.template.diligence.state.DueDiligenceContract;
import com.template.diligence.state.DueDiligenceContract.Commands.Certify;
import com.template.proposal.state.SalesProposal;
import com.template.proposal.state.SalesProposalContract;
import kotlin.Pair;
import net.corda.core.contracts.*;
import net.corda.core.crypto.SecureHash;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public interface SalesProposalAcceptFlows {

    /**
     * Its responder flow is {@link AcceptSimpleHandlerFlow}.
     */
    @StartableByRPC
    @InitiatingFlow
    class AcceptSimpleFlow extends FlowLogic<SignedTransaction> {

        private final static ProgressTracker.Step FETCHING_PROPOSAL = new ProgressTracker.Step("Fetching proposal from the vault.");
        private final static ProgressTracker.Step PASSING_ON = new ProgressTracker.Step("Passing on to AcceptFlow.") {
            @NotNull
            @Override
            public ProgressTracker childProgressTracker() {
                return AcceptFlow.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(FETCHING_PROPOSAL, PASSING_ON);
        }

        @NotNull
        private final UniqueIdentifier proposalId;
        @Nullable
        private final UniqueIdentifier dueDiligenceId;
        @Nullable
        private final DiligenceOracleUtilities.Status diligenceStatus;
        @NotNull
        private final ProgressTracker progressTracker;

        public AcceptSimpleFlow(
                @NotNull final UniqueIdentifier proposalId,
                @Nullable final UniqueIdentifier dueDiligenceId,
                @Nullable final DiligenceOracleUtilities.Status diligenceStatus,
                @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (proposalId == null) throw new NullPointerException("The proposalId cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            this.proposalId = proposalId;
            this.dueDiligenceId = dueDiligenceId;
            this.diligenceStatus = diligenceStatus;
            this.progressTracker = progressTracker;
        }

        public AcceptSimpleFlow(@NotNull final UniqueIdentifier proposalId) {
            this(proposalId, null, null, tracker());
        }

        public AcceptSimpleFlow(
                @NotNull final UniqueIdentifier proposalId,
                @NotNull final UniqueIdentifier dueDiligenceId,
                @NotNull final DiligenceOracleUtilities.Status diligenceStatus) {
            this(proposalId, dueDiligenceId, diligenceStatus, tracker());
        }

        @Suspendable
        @NotNull
        @Override
        public SignedTransaction call() throws FlowException {
            progressTracker.setCurrentStep(FETCHING_PROPOSAL);
            final StateAndRef<SalesProposal> proposal = new SalesProposalUtils(this)
                    .findBy(proposalId.getId());
            final StateAndRef<DueDiligence> dueDiligence;
            if (dueDiligenceId != null) {
                dueDiligence = new DueDiligenceFlowUtils(this)
                        .findBy(dueDiligenceId.getId());
            } else dueDiligence = null;

            // We need to have been informed about this possibly anonymous identity ahead of time.
            progressTracker.setCurrentStep(PASSING_ON);
            return subFlow(new AcceptFlow(proposal, dueDiligence, diligenceStatus, PASSING_ON.childProgressTracker()) {
                @NotNull
                @Override
                protected QueryCriteria getHeldByBuyer(
                        @NotNull final IssuedTokenType issuedCurrency,
                        @NotNull final AbstractParty buyer) {
                    return QueryUtilitiesKt.heldTokenAmountCriteria(issuedCurrency.getTokenType(), buyer);
                }
            });
        }
    }

    /**
     * Its handler is {@link AcceptHandlerFlow}.
     */
    abstract class AcceptFlow extends FlowLogic<SignedTransaction> {

        private final static Step GENERATING_TRANSACTION = new Step("Generating transaction based on parameters.");
        private final static Step MOVING_ASSET_TO_BUYER = new Step("Adding asset to buyer.");
        private final static Step PREPARING_TOKENS_FOR_PAYMENT = new Step("Preparing tokens for payment.");
        private final static Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final static Step RESOLVING_SELLER = new Step("Resolving host of seller.");
        private final static Step SENDING_STATE_REFS = new Step("Sending token state and refs.");
        private final static Step SENDING_MISSING_KEYS = new Step("Sending potentially missing keys.");
        private final static Step SIGNING_TRANSACTION = new Step("Signing transaction with our private keys.");
        private final static Step ASKING_ORACLE = new Step("Asking oracle for certification.");
        private final static Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final static Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        @NotNull
        public static ProgressTracker tracker() {
            return new ProgressTracker(
                    GENERATING_TRANSACTION,
                    MOVING_ASSET_TO_BUYER,
                    PREPARING_TOKENS_FOR_PAYMENT,
                    VERIFYING_TRANSACTION,
                    RESOLVING_SELLER,
                    SENDING_STATE_REFS,
                    SENDING_MISSING_KEYS,
                    SIGNING_TRANSACTION,
                    ASKING_ORACLE,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION);
        }

        @NotNull
        private final StateAndRef<SalesProposal> proposalRef;
        @Nullable
        private final StateAndRef<DueDiligence> dueDiligenceRef;
        @Nullable
        private final DiligenceOracleUtilities.Status diligenceStatus;
        @NotNull
        private final ProgressTracker progressTracker;

        public AcceptFlow(@NotNull final StateAndRef<SalesProposal> proposalRef,
                          @Nullable final StateAndRef<DueDiligence> dueDiligenceRef,
                          @Nullable final DiligenceOracleUtilities.Status diligenceStatus,
                          @NotNull final ProgressTracker progressTracker) {
            //noinspection ConstantConditions
            if (proposalRef == null) throw new NullPointerException("The proposalRef cannot be null");
            //noinspection ConstantConditions
            if (progressTracker == null) throw new NullPointerException("The progressTracker cannot be null");
            if (dueDiligenceRef != null && diligenceStatus == null) {
                throw new NullPointerException("If dueDiligenceRef is not null, diligenceStatus cannot be null");
            }
            this.proposalRef = proposalRef;
            this.dueDiligenceRef = dueDiligenceRef;
            this.diligenceStatus = diligenceStatus;
            this.progressTracker = progressTracker;
        }

        @SuppressWarnings("unused")
        public AcceptFlow(@NotNull final StateAndRef<SalesProposal> proposalRef,
                          @NotNull final StateAndRef<DueDiligence> dueDiligenceRef,
                          @NotNull final DiligenceOracleUtilities.Status diligenceStatus) {
            this(proposalRef, dueDiligenceRef, diligenceStatus, tracker());
        }

        @SuppressWarnings("unused")
        public AcceptFlow(@NotNull final StateAndRef<SalesProposal> proposalRef) {
            this(proposalRef, null, null, tracker());
        }

        @SuppressWarnings("unused")
        public AcceptFlow(@NotNull final StateAndRef<SalesProposal> proposalRef,
                          @NotNull final ProgressTracker progressTracker) {
            this(proposalRef, null, null, progressTracker);
        }

        @NotNull
        abstract protected QueryCriteria getHeldByBuyer(
                @NotNull final IssuedTokenType issuedCurrency,
                @NotNull final AbstractParty buyer) throws FlowException;

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final SalesProposal proposal = proposalRef.getState().getData();
            final NonFungibleToken asset = proposal.getAsset().resolve(getServiceHub()).getState().getData();
            final DueDiligence dueDil;
            if (dueDiligenceRef != null) dueDil = dueDiligenceRef.getState().getData();
            else dueDil = null;

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final TransactionBuilder builder = new TransactionBuilder(proposalRef.getState().getNotary())
                    // Accept the sales proposal
                    .addInputState(proposalRef)
                    .addCommand(new SalesProposalContract.Commands.Accept(),
                            Collections.singletonList(proposal.getBuyer().getOwningKey()))
                    .setTimeWindow(TimeWindow.untilOnly(proposal.getExpirationDate().minus(Duration.ofSeconds(1))));
            if (dueDiligenceRef != null) {
                if (!dueDil.getTokenId().equals(asset.getLinearId())) {
                    throw new FlowException("The due diligence does not match that of the asset");
                }
                //noinspection ConstantConditions
                builder.addInputState(dueDiligenceRef)
                        // We know for sure that diligenceStatus is not null, because dueDiligenceRef is not null.
                        .addCommand(new Certify(asset.getLinearId(), diligenceStatus),
                                dueDil.getOracle().getOwningKey());
                final Instant diligenceValid = Instant.now().plus(DiligenceOracleUtilities.VALID_DURATION);
                if (diligenceValid.isBefore(proposal.getExpirationDate()))
                    // Overwrite time-window
                    builder.setTimeWindow(TimeWindow.untilOnly(diligenceValid.minus(Duration.ofSeconds(1))));
            }

            progressTracker.setCurrentStep(MOVING_ASSET_TO_BUYER);
            MoveTokensUtilitiesKt.addMoveNonFungibleTokens(builder, getServiceHub(),
                    asset.getToken().getTokenType(), proposal.getBuyer());

            progressTracker.setCurrentStep(PREPARING_TOKENS_FOR_PAYMENT);
            final IssuedTokenType issuedCurrency = proposal.getPrice().getToken();
            final QueryCriteria heldByBuyer = getHeldByBuyer(issuedCurrency, proposal.getBuyer());
            final Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> moniesInOut =
                    new SalesProposalUtils(this).generateMove(proposal, heldByBuyer);
            MoveTokensUtilitiesKt.addMoveTokens(builder, moniesInOut.getFirst(), moniesInOut.getSecond());

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            builder.verify(getServiceHub());

            progressTracker.setCurrentStep(RESOLVING_SELLER);
            final Party sellerHost = getServiceHub().getIdentityService()
                    .requireWellKnownPartyFromAnonymous(proposal.getSeller());
            final FlowSession sellerSession = initiateFlow(sellerHost);

            progressTracker.setCurrentStep(SENDING_STATE_REFS);
            // Send potentially missing StateRefs blindly.
            final ArrayList<StateAndRef<? extends ContractState>> allStateRef =
                    new ArrayList<>(moniesInOut.getFirst());
            if (dueDiligenceRef != null) allStateRef.add(dueDiligenceRef);
            subFlow(new SendStateAndRefFlow(sellerSession, allStateRef));

            progressTracker.setCurrentStep(SENDING_MISSING_KEYS);
            // Send potentially missing keys blindly.
            final List<AbstractParty> moniesKeys = moniesInOut.getFirst().stream()
                    .map(it -> it.getState().getData().getHolder())
                    .collect(Collectors.toList());
            final List<AbstractParty> allKeys = new ArrayList<>(moniesKeys);
            if (dueDil != null) {
                allKeys.addAll(dueDil.getParticipants());
                allKeys.add(dueDil.getOracle());
            }
            subFlow(new SyncKeyMappingFlow(sellerSession, allKeys));

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final List<PublicKey> ourKeys = moniesKeys.stream()
                    .map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList());
            ourKeys.add(proposal.getBuyer().getOwningKey());
            if (dueDil != null) {
                getServiceHub().getKeyManagementService()
                        .filterMyKeys(dueDil.getParticipants()
                                .stream()
                                .map(AbstractParty::getOwningKey)
                                .collect(Collectors.toList()))
                        .forEach(ourKeys::add);
            }
            final SignedTransaction acceptTx = getServiceHub().signInitialTransaction(builder, ourKeys);

            progressTracker.setCurrentStep(ASKING_ORACLE);
            final SignedTransaction certifiedTx;
            if (dueDil != null) {
                final TransactionSignature oracleSig = subFlow(new DueDiligenceOracleFlows.Certify.Request(
                        dueDil.getOracle(),
                        acceptTx.getTx()));
                certifiedTx = acceptTx.withAdditionalSignature(oracleSig);
            } else {
                certifiedTx = acceptTx;
            }

            progressTracker.setCurrentStep(GATHERING_SIGS);
            final SignedTransaction signedTx = subFlow(new CollectSignaturesFlow(
                    certifiedTx,
                    Collections.singletonList(sellerSession),
                    ourKeys,
                    GATHERING_SIGS.childProgressTracker()));

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            return subFlow(new FinalityFlow(
                    signedTx,
                    Collections.singletonList(sellerSession),
                    FINALISING_TRANSACTION.childProgressTracker()));
        }
    }

    @InitiatedBy(AcceptSimpleFlow.class)
    class AcceptSimpleHandlerFlow extends AcceptHandlerFlow {

        public AcceptSimpleHandlerFlow(@NotNull FlowSession buyerSession) {
            super(buyerSession);
        }
    }

    /**
     * It is the handler of {@link AcceptFlow}.
     */
    class AcceptHandlerFlow extends FlowLogic<SignedTransaction> {

        @NotNull
        private final FlowSession buyerSession;

        @SuppressWarnings("unused")
        public AcceptHandlerFlow(@NotNull final FlowSession buyerSession) {
            //noinspection ConstantConditions
            if (buyerSession == null) throw new NullPointerException("The buyerSession cannot be null");
            this.buyerSession = buyerSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Potentially missing StateRefs
            subFlow(new ReceiveStateAndRefFlow<>(buyerSession));
            // Receive potentially missing keys.
            subFlow(new SyncKeyMappingFlowHandler(buyerSession));

            // Sign as required.
            final SecureHash txId = subFlow(new SignTransactionFlow(buyerSession) {
                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    // Let's make sure there is an Accept command.
                    final List<Command<?>> commands = stx.getTx().getCommands().stream()
                            .filter(it -> it.getValue() instanceof SalesProposalContract.Commands.Accept)
                            .collect(Collectors.toList());
                    if (commands.size() != 1)
                        throw new FlowException("There is no accept command");

                    final List<SalesProposal> proposals = new ArrayList<>(1);
                    final List<NonFungibleToken> assetsIn = new ArrayList<>(1);
                    final List<FungibleToken> moniesIn = new ArrayList<>(stx.getInputs().size());
                    final List<Command<DueDiligenceContract.Commands>> certifyCommands = new ArrayList<>();
                    for (final StateRef ref : stx.getInputs()) {
                        final ContractState state = getServiceHub().toStateAndRef(ref).getState().getData();
                        if (state instanceof SalesProposal)
                            proposals.add((SalesProposal) state);
                        else if (state instanceof NonFungibleToken)
                            assetsIn.add((NonFungibleToken) state);
                        else if (state instanceof FungibleToken)
                            moniesIn.add((FungibleToken) state);
                        else if (state instanceof DueDiligence) {
                            // Make sure there is a Certify command
                            //noinspection unchecked
                            certifyCommands.addAll(stx.getTx().getCommands().stream()
                                    .filter(it -> it.getValue() instanceof DueDiligenceContract.Commands)
                                    .map(it -> (Command<DueDiligenceContract.Commands>) it)
                                    .collect(Collectors.toList()));
                        } else
                            throw new FlowException("Unexpected state class: " + state.getClass());
                    }
                    if (proposals.size() != 1) throw new FlowException("There should be a single sales proposal in");
                    if (assetsIn.size() != 1) throw new FlowException("There should be a single asset in");
                    final SalesProposal proposal = proposals.get(0);
                    final NonFungibleToken assetIn = assetsIn.get(0);
                    // If the asset does not match the proposal, it will be caught in the contract.

                    // Let's check due diligence.
                    if (!certifyCommands.isEmpty()) {
                        if (certifyCommands.size() != 1)
                            throw new FlowException("Found more than 1 due diligence command");
                        if (!(certifyCommands.get(0).getValue() instanceof Certify))
                            throw new FlowException("Found a due diligence command but not Certify");
                        final Certify certify = (Certify) certifyCommands.get(0).getValue();
                        if (!certify.getTokenId().equals(assetIn.getLinearId()))
                            throw new FlowException("The due diligence is not for this token");
                    }

                    // Let's make sure we are signing with a single key.
                    final List<PublicKey> allInputKeys = moniesIn.stream()
                            .map(it -> it.getHolder().getOwningKey())
                            .collect(Collectors.toList());
                    allInputKeys.add(assetIn.getHolder().getOwningKey());
                    final List<PublicKey> myKeys = StreamSupport.stream(
                            getServiceHub().getKeyManagementService().filterMyKeys(allInputKeys).spliterator(),
                            false)
                            .collect(Collectors.toList());
                    if (myKeys.size() != 1) throw new FlowException("There are not the expected keys of mine");
                    if (!myKeys.get(0).equals(proposal.getSeller().getOwningKey()))
                        throw new FlowException("The key of mine is not the seller");

                    // Let's make sure the buyer is not trying to pass off some of our own monies as payment...
                    // After all, we are going to sign this transaction.
                    final List<FungibleToken> myInMonies = moniesIn.stream()
                            .filter(it -> it.getHolder().equals(proposal.getSeller()))
                            .collect(Collectors.toList());
                    if (!myInMonies.isEmpty())
                        throw new FlowException("There is a FungibleToken of mine in input");

                    // That I am paid is covered by the sales proposal contract.
                }
            }).getId();

            return subFlow(new ReceiveFinalityFlow(buyerSession, txId));
        }
    }

}
