package com.template.contracts;

import com.template.states.TokenState;
import net.corda.testing.core.TestIdentity;
import org.jetbrains.annotations.NotNull;

class TestHelpers {

    @NotNull
    static TokenState createToken(
            @NotNull final TestIdentity issuer,
            @NotNull final TestIdentity holder,
            final long quantity) {
        return new TokenState(issuer.getParty(), holder.getParty(), quantity);
    }

}
