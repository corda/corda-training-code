package com.template.proposal.state;

import com.r3.corda.lib.tokens.contracts.FungibleTokenContract;
import com.r3.corda.lib.tokens.contracts.NonFungibleTokenContract;
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand;
import com.r3.corda.lib.tokens.contracts.states.AbstractToken;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import net.corda.core.identity.Party;
import net.corda.core.transactions.WireTransaction;
import net.corda.testing.dsl.LedgerDSL;
import net.corda.testing.dsl.TestLedgerDSLInterpreter;
import net.corda.testing.dsl.TestTransactionDSLInterpreter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public interface SalesProposalContractTestHelpers {
    @NotNull
    static WireTransaction issueToken(
            @NotNull final LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> ledger,
            @NotNull final Party issuer,
            @NotNull final AbstractToken toIssue) {
        return ledger.transaction(tx -> {
            if (toIssue instanceof NonFungibleToken)
                tx.output(NonFungibleTokenContract.Companion.getContractId(), toIssue);
            else if (toIssue instanceof FungibleToken)
                tx.output(FungibleTokenContract.Companion.getContractId(), toIssue);
            else
                throw new IllegalArgumentException("Unknown token type " + toIssue.getClass());
            tx.command(Collections.singletonList(issuer.getOwningKey()),
                    new IssueTokenCommand(toIssue.getIssuedTokenType(), Collections.singletonList(0)));
            return tx.verifies();
        });
    }
}
