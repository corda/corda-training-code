package com.template.diligence.state;

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

public class DueDiligenceContractDropTests {

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
                final UniqueIdentifier id = new UniqueIdentifier();
                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID, new DummyState(alice, bob));
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new DueDiligenceContract.Commands.Drop());
                tx.failsWith("There should be a single due diligence input on drop");

                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Collections.singletonList(alice)));
                tx.verifies();

                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Collections.singletonList(alice)));
                return tx.failsWith("There should be a single due diligence input on drop");
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
                tx.command(Collections.singletonList(alice.getOwningKey()),
                        new DueDiligenceContract.Commands.Drop());
                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Collections.singletonList(alice)));
                tx.verifies();

                tx.output(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Collections.singletonList(alice)));
                return tx.failsWith("There should be no due diligence outputs on drop");
            });
            return null;
        });
    }

    @Test
    public void theParticipantsShouldBeRequiredSigners() {
        ledger(ledgerServices, ledger -> {
            ledger.transaction(tx -> {
                final UniqueIdentifier id = new UniqueIdentifier();
                tx.input(DueDiligenceContract.DUE_DILIGENCE_CONTRACT_ID,
                        new DueDiligence(new UniqueIdentifier(), id, dmv, Arrays.asList(alice, bob)));

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(alice.getOwningKey()),
                            new DueDiligenceContract.Commands.Drop());
                    return txCopy.failsWith("The participants should be the only signers on drop");
                });

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(bob.getOwningKey()),
                            new DueDiligenceContract.Commands.Drop());
                    return txCopy.failsWith("The participants should be the only signers on drop");
                });

                tx.tweak(txCopy -> {
                    txCopy.command(Collections.singletonList(dmv.getOwningKey()),
                            new DueDiligenceContract.Commands.Drop());
                    return txCopy.failsWith("The participants should be the only signers on drop");
                });

                tx.command(Arrays.asList(alice.getOwningKey(), bob.getOwningKey()),
                        new DueDiligenceContract.Commands.Drop());
                return tx.verifies();
            });
            return null;
        });
    }

}