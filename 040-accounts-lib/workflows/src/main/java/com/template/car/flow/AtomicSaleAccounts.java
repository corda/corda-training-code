package com.template.car.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.ci.workflows.ProvideKeyFlow;
import com.r3.corda.lib.ci.workflows.RequestKeyFlow;
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilitiesKt;
import com.r3.corda.lib.tokens.selection.TokenQueryBy;
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection;
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensUtilitiesKt;
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow;
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt;
import com.template.car.state.CarTokenType;
import kotlin.Pair;
import net.corda.core.contracts.*;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.r3.corda.lib.tokens.selection.database.config.DatabaseSelectionConfigKt.*;
import static com.r3.corda.lib.tokens.workflows.utilities.QueryUtilitiesKt.heldTokenCriteria;

public interface AtomicSaleAccounts {

    @InitiatingFlow
    class CarSeller extends FlowLogic<SignedTransaction> {

        @NotNull
        private final TokenPointer<CarTokenType> car;
        @NotNull
        private final UUID buyer;
        @NotNull
        private final IssuedTokenType issuedCurrency;

        public CarSeller(
                @NotNull final TokenPointer<CarTokenType> car,
                @NotNull final UUID buyer,
                @NotNull final IssuedTokenType issuedCurrency) {
            //noinspection ConstantConditions
            if (car == null) throw new NullPointerException("The car cannot be null");
            //noinspection ConstantConditions
            if (buyer == null) throw new NullPointerException("The buyer cannot be null");
            //noinspection ConstantConditions
            if (issuedCurrency == null) throw new NullPointerException("The issuedCurrency cannot be null");
            this.car = car;
            this.buyer = buyer;
            this.issuedCurrency = issuedCurrency;
        }

        @Suspendable
        @Override
        @NotNull
        public SignedTransaction call() throws FlowException {
            // Fetch the latest known state.
            final StateAndRef<CarTokenType> carInfo = car.getPointer().resolve(getServiceHub());
            final AccountService accountService = UtilitiesKt.getAccountService(this);
            // We need to have been informed about any account ahead of time.
            final StateAndRef<AccountInfo> buyerAccount = accountService.accountInfo(buyer);
            if (buyerAccount == null) throw new FlowException("This buyer account is unknown: " + buyer);
            final Party buyerHost = buyerAccount.getState().getData().getHost();
            final FlowSession buyerSession = initiateFlow(buyerHost);
            // The host needs to know which of its accounts is buying.
            buyerSession.send(buyer);
            // Send the car information to the buyer. A bit ahead of time so that it can fetch states while we do too.
            subFlow(new SendStateAndRefFlow(buyerSession, Collections.singletonList(carInfo)));

            final long price = carInfo.getState().getData().getPrice();
            final QueryCriteria tokenCriteria = heldTokenCriteria(car);
            final List<StateAndRef<NonFungibleToken>> ownedCarTokens = getServiceHub().getVaultService()
                    .queryBy(NonFungibleToken.class, tokenCriteria).getStates();
            if (ownedCarTokens.size() != 1) throw new FlowException("NonFungibleToken not found");
            final AbstractParty seller = ownedCarTokens.get(0).getState().getData().getHolder();

            // Send the proof that we own the car.
            subFlow(new SendStateAndRefFlow(buyerSession, ownedCarTokens));

            // Send the currency desired.
            buyerSession.send(issuedCurrency);

            // Which key to use, let's piggy-back on this session anyway.
            final AnonymousParty buyerParty = subFlow(new RequestKeyFlow(
                    buyerSession, buyerAccount.getState().getData().getIdentifier().getId()));

            // Prepare the transaction. The only notary the seller and buyer have no control over is that of the
            // car info. So we have to pick this one.
            final Party notary = carInfo.getState().getNotary();
            final TransactionBuilder txBuilder = new TransactionBuilder(notary);

            // Create a proposal to move the car token to Bob.
            final PartyAndToken carForBuyer = new PartyAndToken(buyerParty, car);
            MoveTokensUtilitiesKt.addMoveNonFungibleTokens(txBuilder, getServiceHub(), carForBuyer, null);

            // Receive the currency states that will go in input.
            final List<StateAndRef<FungibleToken>> currencyInputs = subFlow(new ReceiveStateAndRefFlow<>(buyerSession));
            // Let's make sure the buyer is not trying to pass off some of our own dollars as payment... After all, we
            // are going to sign this transaction.
            final long holderCurrencyInputCount = currencyInputs.stream()
                    // Since we are going to sign with this holder key, we should make sure no money from it is coming
                    // in
                    .filter(it -> it.getState().getData().getHolder().equals(seller))
                    .count();
            if (holderCurrencyInputCount != 0)
                throw new FlowException("The buyer sent us some of the holder's token states: " +
                        holderCurrencyInputCount);
            // Other than that, we do not care much about the inputs as we expect that any error will be caught by the
            // contract.

            // Receive the currency states that will go in output.
            // noinspection unchecked
            final List<FungibleToken> currencyOutputs = buyerSession.receive(List.class).unwrap(it -> it);
            final long sumPaid = currencyOutputs.stream()
                    // Are they owned by the seller (in the future)? We don't care about the "change".
                    .filter(it -> it.getHolder().equals(seller))
                    .map(FungibleToken::getAmount)
                    // Are they of the expected currency?
                    .filter(it -> it.getToken().equals(issuedCurrency))
                    .map(Amount::getQuantity)
                    .reduce(0L, Math::addExact);
            // Make sure the holder was paid.
            if (sumPaid < AmountUtilitiesKt.amount(price, issuedCurrency.getTokenType()).getQuantity())
                throw new FlowException("We were paid only " +
                        sumPaid / AmountUtilitiesKt.amount(1L, issuedCurrency.getTokenType()).getQuantity() +
                        " instead of the expected " + price);

            // Put those currency states where they belong.
            MoveTokensUtilitiesKt.addMoveTokens(txBuilder, currencyInputs, currencyOutputs);

            // Sign the transaction and send it to buyer for signature.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder,
                    // The seller signs, not the host.
                    seller.getOwningKey());
            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partSignedTx,
                    Collections.singletonList(buyerSession),
                    // We tell we already signed with our own or our account's key.
                    Collections.singleton(seller.getOwningKey())));

            // Distribute updates of the evolvable car token.
            subFlow(new UpdateDistributionListFlow(fullySignedTx));

            // Finalise the transaction
            return subFlow(new FinalityFlow(fullySignedTx, Collections.singletonList(buyerSession)));
        }
    }

    @SuppressWarnings("unused")
    @InitiatedBy(CarSeller.class)
    class CarBuyer extends FlowLogic<SignedTransaction> {
        @NotNull
        private final FlowSession sellerSession;

        @SuppressWarnings("unused")
        public CarBuyer(@NotNull final FlowSession sellerSession) {
            this.sellerSession = sellerSession;
        }

        @Suspendable
        @Override
        @NotNull
        public SignedTransaction call() throws FlowException {
            // Receive the buyer information
            final UUID buyer = sellerSession.receive(UUID.class).unwrap(it -> it);
            final AccountService accountService = UtilitiesKt.getAccountService(this);
            // We need to have been informed about any account ahead of time.
            final StateAndRef<AccountInfo> buyerAccount = accountService.accountInfo(buyer);
            if (buyerAccount == null) throw new FlowException("This buyer account is unknown: " + buyer);
            if (!buyerAccount.getState().getData().getHost().equals(getOurIdentity()))
                throw new FlowException("We are not this account's host");
            // Receive the car information. We will resolve the car type right after, from the NonFungibleToken, but
            // we have to receive for now.
            final List<StateAndRef<CarTokenType>> carInfos = subFlow(new ReceiveStateAndRefFlow<>(sellerSession));
            if (carInfos.size() != 1) throw new FlowException("We expected a single car type");
            final StateAndRef<CarTokenType> carInfo = carInfos.get(0);
            final long price = carInfo.getState().getData().getPrice();
            // Receive the owned car information.
            final List<StateAndRef<NonFungibleToken>> heldCarInfos = subFlow(new ReceiveStateAndRefFlow<>(sellerSession));
            if (heldCarInfos.size() != 1) throw new FlowException("We expected a single held car");
            final StateAndRef<NonFungibleToken> heldCarInfo = heldCarInfos.get(0);
            // Is this the same car?
            //noinspection unchecked
            if (!((TokenPointer<CarTokenType>) heldCarInfo.getState().getData().getTokenType())
                    .getPointer().getPointer()
                    .equals(carInfo.getState().getData().getLinearId()))
                throw new FlowException("The owned car does not correspond to the earlier car info.");

            // TODO have an internal check that this is indeed the car that the buyer intends to buy.

            // Receive the currency information.
            final IssuedTokenType issuedCurrency = sellerSession.receive(IssuedTokenType.class).unwrap(it -> it);
            // Send a new key, and keep it here for later verification.
            final AnonymousParty buyerParty = subFlow(new ProvideKeyFlow(sellerSession));

            // TODO have an internal check that this is indeed the currency that the buyer decided to use in the sale.

            // Assemble the currency states. Notice we search by external id.
            final QueryCriteria heldByBuyer = new QueryCriteria.VaultQueryCriteria()
                    .withExternalIds(Collections.singletonList(buyer));
            final QueryCriteria properlyIssued = QueryUtilitiesKt.tokenAmountWithIssuerCriteria(
                    issuedCurrency.getTokenType(), issuedCurrency.getIssuer());
            // Have the utility do the "dollars to cents" conversion for us.
            final Amount<TokenType> priceInCurrency = AmountUtilitiesKt.amount(price, issuedCurrency.getTokenType());
            // Generate the buyer's currency inputs, to be spent, and the outputs, the currency tokens that will be
            // held by the seller.
            final DatabaseTokenSelection tokenSelection = new DatabaseTokenSelection(
                    getServiceHub(), MAX_RETRIES_DEFAULT, RETRY_SLEEP_DEFAULT, RETRY_CAP_DEFAULT, PAGE_SIZE_DEFAULT);
            final Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> inputsAndOutputs = tokenSelection.generateMove(
                    // Eventually held by the seller.
                    Collections.singletonList(new Pair<>(heldCarInfo.getState().getData().getHolder(), priceInCurrency)),
                    // We see here that we should not rely on the default value, because the new key of the buyer keeps
                    // the change.
                    buyerParty,
                    new TokenQueryBy(issuedCurrency.getIssuer(), it -> true, heldByBuyer.and(properlyIssued)),
                    getRunId().getUuid());

            // Send the currency states that will go in input, along with their history.
            subFlow(new SendStateAndRefFlow(sellerSession, inputsAndOutputs.getFirst()));

            // Send the currency states that will go in output.
            sellerSession.send(inputsAndOutputs.getSecond());

            // Sign the received transaction.
            final SecureHash signedTxId = subFlow(new SignTransactionFlow(sellerSession) {
                @Override
                // There is an opportunity for a malicious seller to ask the buyer for information, and then ask them
                // to sign a different transaction. So we have to be careful.
                // Make sure this is the transaction we expect: car, price and states we sent.
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    // Recall the inputs we prepared in the first part of the flow.
                    // We can use a Set because all StateRef are truly unique.
                    final Set<StateRef> allKnownInputs = inputsAndOutputs.getFirst().stream()
                            .map(StateAndRef::getRef)
                            .collect(Collectors.toSet());
                    // There should be no extra inputs, other than the car.
                    allKnownInputs.add(heldCarInfo.getRef());
                    final Set<StateRef> allInputs = new HashSet<>(stx.getInputs());
                    if (!allInputs.equals(allKnownInputs))
                        throw new FlowException("Inconsistency in input refs compared to expectation");

                    // Moving on to the outputs.
                    final List<ContractState> allOutputs = stx.getCoreTransaction().getOutputStates();
                    // Let's not pass any unexpected count of outputs.
                    if (allOutputs.size() != inputsAndOutputs.getSecond().size() + 1)
                        throw new FlowException("Wrong count of outputs");

                    // If we keep only those of the proper currency. We have to use a List and cannot use a Set
                    // because 2 "quarters" are equal to each other.
                    final List<FungibleToken> allCurrencyOutputs = allOutputs.stream()
                            .filter(it -> it instanceof FungibleToken)
                            .map(it -> (FungibleToken) it)
                            .filter(it -> it.getIssuedTokenType().equals(issuedCurrency))
                            .collect(Collectors.toList());
                    // Let's not pass if we don't recognise the states we gave, with the additional constraint that
                    // they have to be in the same order.
                    if (!inputsAndOutputs.getSecond().equals(allCurrencyOutputs))
                        throw new FlowException("Inconsistency in FungibleToken outputs compared to expectation");

                    // If we keep only the car tokens.
                    final List<NonFungibleToken> allCarOutputs = allOutputs.stream()
                            .filter(it -> it instanceof NonFungibleToken)
                            .map(it -> (NonFungibleToken) it)
                            .collect(Collectors.toList());
                    // Let's not pass if there is not exactly 1 car.
                    if (allCarOutputs.size() != 1) throw new FlowException("Wrong count of car outputs");
                    // And it has to be the car we expect.
                    final NonFungibleToken outputHeldCar = allCarOutputs.get(0);
                    if (!outputHeldCar.getLinearId().equals(heldCarInfo.getState().getData().getLinearId()))
                        throw new FlowException("This is not the car we expected");
                    if (!outputHeldCar.getHolder().equals(buyerParty))
                        throw new FlowException("The car is not held by the buyer in output");

                    // There should only be 2 move commands.
                    final List<Command<?>> commands = stx.getTx().getCommands();
                    if (commands.size() != 2) throw new FlowException("There are not the 2 expected commands");
                    final List<?> tokenCommands = commands.stream()
                            .map(Command::getValue)
                            .filter(it -> it instanceof MoveTokenCommand)
                            .collect(Collectors.toList());
                    if (tokenCommands.size() != 2)
                        throw new FlowException("There are not the 2 expected move commands");
                }
            }).getId();

            // Finalise the transaction.
            return subFlow(new ReceiveFinalityFlow(sellerSession, signedTxId));
        }
    }
}
