package com.template.diligence.state;

import com.template.diligence.state.DiligenceOracleUtilities.Status;
import com.template.dummy.state.DummyState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.common.internal.ParametersUtilitiesKt;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static net.corda.testing.node.NodeTestUtils.ledger;

public class DueDiligenceContractCertifyTests {

    private final TestIdentity notaryId = new TestIdentity(
            new CordaX500Name("Notary", "Washington D.C.", "US"));
    private final MockServices ledgerServices = new MockServices(
            Arrays.asList("com.r3.corda.lib.tokens.contracts", "com.template.diligence.state"),
            notaryId,
            ParametersUtilitiesKt.testNetworkParameters(Collections.emptyList(), 4));
    private final Party dmv = new TestIdentity(
            new CordaX500Name("DMV", "Austin", "US")).getParty();
    private final AbstractParty alice = new TestIdentity(
            new CordaX500Name("Alice", "London", "GB")).getParty();
    private final AbstractParty bob = new TestIdentity(
            new CordaX500Name("Bob", "New York", "US")).getParty();

    @Test
    public void thereShouldBeASingleDueDiligenceInput() {
        ledger(ledgerServices, ledger -> {
            ledger.transaction(tx -> {
                final UniqueIdentifier tokenId = new UniqueIdentifier();
                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID, new DummyState(alice, bob));
                tx.command(Collections.singletonList(dmv.getOwningKey()),
                        new DueDiligenceContract.Commands.Certify(tokenId, Status.Clear));
                tx.failsWith("There should be a single due diligence input on certify");

                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), tokenId, dmv, Collections.singletonList(alice)));
                tx.verifies();

                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), tokenId, dmv, Collections.singletonList(alice)));
                return tx.failsWith("There should be a single due diligence input on certify");
            });
            return null;
        });
    }

    @Test
    public void thereShouldBeNoDueDiligenceOutputs() {
        ledger(ledgerServices, ledger -> {
            ledger.transaction(tx -> {
                final UniqueIdentifier id = new UniqueIdentifier();
                tx.output(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID, new DummyState(alice, bob));
                tx.command(Collections.singletonList(dmv.getOwningKey()),
                        new DueDiligenceContract.Commands.Certify(id, Status.Linked));
                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Collections.singletonList(alice)));
                tx.verifies();

                tx.output(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Collections.singletonList(alice)));
                return tx.failsWith("There should be no due diligence outputs on certify");
            });
            return null;
        });
    }

    @Test
    public void theTokenIdShouldMatchInTheCommand() {
        ledger(ledgerServices, ledger -> {
            ledger.transaction(tx -> {
                final UniqueIdentifier id = new UniqueIdentifier();
                tx.command(Collections.singletonList(dmv.getOwningKey()),
                        new DueDiligenceContract.Commands.Certify(id, Status.Clear));

                tx.tweak(txCopy -> {
                    txCopy.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                            new DueDiligence(new UniqueIdentifier(), new UniqueIdentifier(),
                                    dmv, Collections.singletonList(alice)));
                    return txCopy.failsWith("The command id should match that of the input");
                });

                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Collections.singletonList(alice)));
                return tx.verifies();
            });
            return null;
        });
    }

    @Test
    public void theOracleShouldBeRequiredSigner() {
        ledger(ledgerServices, ledger -> {
            ledger.transaction(tx -> {
                final UniqueIdentifier id = new UniqueIdentifier();
                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Collections.singletonList(alice)));

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(bob.getOwningKey()),
                            new DueDiligenceContract.Commands.Certify(id, Status.Clear));
                    return txCopy.failsWith("The oracle should be the only signer on certify");
                });

                tx.command(Collections.singletonList(dmv.getOwningKey()),
                        new DueDiligenceContract.Commands.Certify(id, Status.Linked));
                return tx.verifies();
            });
            return null;
        });
    }

}