package com.template.contracts;

import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Corda has "a bug" that if a Contracts CorDapp doesn't contain a contract; it doesn't load it on node startup.
 */
@SuppressWarnings("unused")
public class DummyContract implements Contract {
    private DummyContract() {
        throw new NotImplementedException();
    }

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        throw new NotImplementedException();
    }
}
