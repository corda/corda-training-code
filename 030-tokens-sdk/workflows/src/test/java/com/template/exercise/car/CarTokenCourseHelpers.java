package com.template.exercise.car;

import com.google.common.collect.ImmutableList;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.node.NotaryInfo;
import net.corda.testing.common.internal.ParametersUtilitiesKt;
import net.corda.testing.node.MockNetworkNotarySpec;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.TestCordapp;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public interface CarTokenCourseHelpers {
    CordaX500Name NOTARY = CordaX500Name.parse("O=Gov Notary, L=Washington D.C., C=US");
    CordaX500Name DMV = CordaX500Name.parse("O=DMV, L=New York, C=US");
    CordaX500Name BMW_DEALER = CordaX500Name.parse("O=BMW Dealership, L=New York, C=US");

    @NotNull
    static MockNetworkParameters prepareMockNetworkParameters() throws Exception {
        return new MockNetworkParameters()
                .withNotarySpecs(Collections.singletonList(new MockNetworkNotarySpec(NOTARY)))
                .withCordappsForAllNodes(ImmutableList.of(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.selection"),
                        TestCordapp.findCordapp("com.template.states"),
                        TestCordapp.findCordapp("com.template.flows")))
                .withNetworkParameters(ParametersUtilitiesKt.testNetworkParameters(
                        Collections.emptyList(), 4
                ));
    }
}
