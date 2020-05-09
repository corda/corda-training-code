package com.template.proposal.state;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import net.corda.core.contracts.*;
import net.corda.core.flows.FlowLogicRefFactory;
import net.corda.core.identity.AbstractParty;
import net.corda.core.serialization.ConstructorForDeserialization;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@BelongsToContract(SalesProposalContract.class)
public class SalesProposal implements LinearState, SchedulableState {

    // We need to use a string because the flow is in an another module.
    public static final String SCHEDULED_FLOW = "com.template.proposal.flow.SalesProposalRejectFlows$RejectSimpleFlow";

    @NotNull
    private final UniqueIdentifier linearId;
    @NotNull
    private final StaticPointer<NonFungibleToken> asset;
    @NotNull
    private final AbstractParty seller;
    @NotNull
    private final AbstractParty buyer;
    @NotNull
    private final Amount<IssuedTokenType> price;
    @NotNull
    private final Instant expirationDate;

    @ConstructorForDeserialization
    public SalesProposal(
            @NotNull final UniqueIdentifier linearId,
            @NotNull final StaticPointer<NonFungibleToken> asset,
            @NotNull final AbstractParty seller,
            @NotNull final AbstractParty buyer,
            @NotNull final Amount<IssuedTokenType> price,
            @NotNull final Instant expirationDate) {
        //noinspection ConstantConditions
        if (linearId == null) throw new NullPointerException("linearId cannot be null");
        //noinspection ConstantConditions
        if (asset == null) throw new NullPointerException("asset cannot be null");
        //noinspection ConstantConditions
        if (seller == null) throw new NullPointerException("seller cannot be null");
        //noinspection ConstantConditions
        if (buyer == null) throw new NullPointerException("buyer cannot be null");
        //noinspection ConstantConditions
        if (price == null) throw new NullPointerException("price cannot be null");
        //noinspection ConstantConditions
        if (expirationDate == null) throw new NullPointerException("expirationDate cannot be null");
        this.linearId = linearId;
        this.asset = asset;
        this.seller = seller;
        this.buyer = buyer;
        this.price = price;
        this.expirationDate = expirationDate;
    }

    public SalesProposal(
            @NotNull final UniqueIdentifier linearId,
            @NotNull final StateAndRef<NonFungibleToken> asset,
            @NotNull final AbstractParty buyer,
            @NotNull final Amount<IssuedTokenType> price,
            @NotNull final Instant expirationDate) {
        this(linearId,
                new StaticPointer<>(asset.getRef(), NonFungibleToken.class),
                asset.getState().getData().getHolder(),
                buyer,
                price,
                expirationDate);
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.copyOf(Arrays.asList(seller, buyer));
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @NotNull
    @Override
    public ScheduledActivity nextScheduledActivity(
            @NotNull final StateRef thisStateRef,
            @NotNull final FlowLogicRefFactory flowLogicRefFactory) {
        return new ScheduledActivity(
                flowLogicRefFactory.create(SCHEDULED_FLOW, linearId, seller),
                expirationDate.plus(Duration.ofSeconds(1)));
    }

    @NotNull
    public StaticPointer<NonFungibleToken> getAsset() {
        return asset;
    }

    @NotNull
    public AbstractParty getSeller() {
        return seller;
    }

    @NotNull
    public AbstractParty getBuyer() {
        return buyer;
    }

    @NotNull
    public Amount<IssuedTokenType> getPrice() {
        return price;
    }

    @NotNull
    public Instant getExpirationDate() {
        return expirationDate;
    }

    public boolean isSameAsset(@NotNull final StateAndRef<? extends ContractState> asset) {
        final ContractState aToken = asset.getState().getData();
        if (!(aToken instanceof NonFungibleToken)) return false;
        final NonFungibleToken token = (NonFungibleToken) aToken;
        return this.asset.getPointer().equals(asset.getRef())
                && this.seller.equals(token.getHolder());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SalesProposal that = (SalesProposal) o;
        return linearId.equals(that.linearId) &&
                asset.equals(that.asset) &&
                seller.equals(that.seller) &&
                buyer.equals(that.buyer) &&
                price.equals(that.price) &&
                expirationDate.equals(that.expirationDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linearId, asset, seller, buyer, price, expirationDate);
    }
}
