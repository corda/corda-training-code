package com.template.flows;

import net.corda.core.identity.CordaX500Name;

public interface Constants {
    String desiredNotaryName = "O=Notary, L=London, C=GB";
    CordaX500Name desiredNotary = CordaX500Name.parse(desiredNotaryName);
}
