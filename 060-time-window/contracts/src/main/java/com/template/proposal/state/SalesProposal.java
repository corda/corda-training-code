package com.template.proposal.state;

import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@BelongsToContract(SalesProposalContract.class)
public class SalesProposal implements LinearState {

    @NotNull
    private final UniqueIdentifier linearId;
    @NotNull
    private final StateAndRef<NonFungibleToken> asset;
    @NotNull
    private final UniqueIdentifier assetId;
    @NotNull
    private final AbstractParty seller;
    @NotNull
    private final AbstractParty buyer;
    @NotNull
    private final Amount<IssuedTokenType> price;
    @NotNull
    private final Instant lastValidity;

    public SalesProposal(
            @NotNull final UniqueIdentifier linearId,
            @NotNull final StateAndRef<NonFungibleToken> asset,
            @NotNull final AbstractParty buyer,
            @NotNull final Amount<IssuedTokenType> price,
            @NotNull final Instant lastValidity) {
        //noinspection ConstantConditions
        if (linearId == null) throw new NullPointerException("linearId cannot be null");
        //noinspection ConstantConditions
        if (asset == null) throw new NullPointerException("assetId cannot be null");
        //noinspection ConstantConditions
        if (buyer == null) throw new NullPointerException("buyer cannot be null");
        //noinspection ConstantConditions
        if (price == null) throw new NullPointerException("price cannot be null");
        //noinspection ConstantConditions
        if (lastValidity == null) throw new NullPointerException("lastValidity cannot be null");
        this.linearId = linearId;
        this.asset = asset;
        this.assetId = asset.getState().getData().getLinearId();
        this.seller = asset.getState().getData().getHolder();
        this.buyer = buyer;
        this.price = price;
        this.lastValidity = lastValidity;
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
    public StateAndRef<NonFungibleToken> getAsset() {
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

    @NotNull
    public Instant getLastValidity() {
        return lastValidity;
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
                lastValidity.equals(that.lastValidity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linearId, asset, seller, buyer, price, lastValidity);
    }
}
