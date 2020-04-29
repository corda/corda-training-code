package com.template.contracts;

import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

/**
 * Corda has "a bug" that if a "Contracts" CorDapp doesn't contain a contract, it doesn't load the Cordapp
 * on node startup.
 */
@SuppressWarnings("unused")
public class DummyContract implements Contract {
    private DummyContract() {
        throw new NotImplementedException("This contract is not to be used");
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        throw new NotImplementedException("This contract is not to be used");
    }
}
