package com.template.car.flow;

import com.google.common.collect.ImmutableList;
import com.template.car.flow.CarTokenTypeConstants;
import net.corda.testing.common.internal.ParametersUtilitiesKt;
import net.corda.testing.node.MockNetworkNotarySpec;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.TestCordapp;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public interface CarTokenCourseHelpers {
    @NotNull
    static MockNetworkParameters prepareMockNetworkParameters() throws Exception {
        return new MockNetworkParameters()
                .withNotarySpecs(Collections.singletonList(new MockNetworkNotarySpec(CarTokenTypeConstants.NOTARY)))
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
