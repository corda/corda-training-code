package com.template.proposal.state;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.serialization.ConstructorForDeserialization;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@BelongsToContract(SalesProposalContract.class)
public class SalesProposal implements LinearState {

    @NotNull
    private final UniqueIdentifier linearId;
    @NotNull
    private final StaticPointer<NonFungibleToken> asset;
    @NotNull
    private final UniqueIdentifier assetId;
    @NotNull
    private final AbstractParty seller;
    @NotNull
    private final AbstractParty buyer;
    @NotNull
    private final Amount<IssuedTokenType> price;

    @ConstructorForDeserialization
    public SalesProposal(
            @NotNull final UniqueIdentifier linearId,
            @NotNull final StaticPointer<NonFungibleToken> asset,
            @NotNull final UniqueIdentifier assetId,
            @NotNull final AbstractParty seller,
            @NotNull final AbstractParty buyer,
            @NotNull final Amount<IssuedTokenType> price) {
        //noinspection ConstantConditions
        if (linearId == null) throw new NullPointerException("linearId cannot be null");
        //noinspection ConstantConditions
        if (asset == null) throw new NullPointerException("asset cannot be null");
        //noinspection ConstantConditions
        if (assetId == null) throw new NullPointerException("assetId cannot be null");
        //noinspection ConstantConditions
        if (seller == null) throw new NullPointerException("seller cannot be null");
        //noinspection ConstantConditions
        if (buyer == null) throw new NullPointerException("buyer cannot be null");
        //noinspection ConstantConditions
        if (price == null) throw new NullPointerException("price cannot be null");
        this.linearId = linearId;
        this.asset = asset;
        this.assetId = assetId;
        this.seller = seller;
        this.buyer = buyer;
        this.price = price;
    }

    public SalesProposal(
            @NotNull final UniqueIdentifier linearId,
            @NotNull final StateAndRef<NonFungibleToken> asset,
            @NotNull final AbstractParty buyer,
            @NotNull final Amount<IssuedTokenType> price) {
        this(linearId,
                new StaticPointer<>(asset.getRef(), NonFungibleToken.class),
                asset.getState().getData().getLinearId(),
                asset.getState().getData().getHolder(),
                buyer,
                price);
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
    public StaticPointer<NonFungibleToken> getAsset() {
        return asset;
    }

    @NotNull
    public UniqueIdentifier getAssetId() {
        return assetId;
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

    public boolean isSameAsset(@NotNull final StateAndRef<? extends ContractState> asset) {
        final ContractState aToken = asset.getState().getData();
        if (!(aToken instanceof NonFungibleToken)) return false;
        final NonFungibleToken token = (NonFungibleToken) aToken;
        return this.asset.getPointer().equals(asset.getRef())
                && this.assetId.equals(token.getLinearId())
                && this.seller.equals(token.getHolder());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SalesProposal that = (SalesProposal) o;
        return linearId.equals(that.linearId) &&
                asset.equals(that.asset) &&
                assetId.equals(that.assetId) &&
                seller.equals(that.seller) &&
                buyer.equals(that.buyer) &&
                price.equals(that.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linearId, asset, assetId, seller, buyer, price);
    }
}
