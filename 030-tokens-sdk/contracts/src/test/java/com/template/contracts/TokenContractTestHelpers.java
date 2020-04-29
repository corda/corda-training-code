package com.template.contracts;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.template.states.AirMileType;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

public interface TokenContractTestHelpers {
    @NotNull
    static FungibleToken create(
            @NotNull final IssuedTokenType tokenType,
            @NotNull final Party holder,
            final long quantity) {
        return new FungibleToken(new Amount<>(quantity, tokenType), holder, AirMileType.getContractAttachment());
    }
}
