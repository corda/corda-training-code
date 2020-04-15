package com.template.car.flow;

import net.corda.core.identity.CordaX500Name;

public interface CarTokenTypeConstants {
    CordaX500Name NOTARY = CordaX500Name.parse("O=Gov Notary, L=Washington D.C., C=US");
    CordaX500Name DMV = CordaX500Name.parse("O=DMV, L=New York, C=US");
    CordaX500Name BMW_DEALER = CordaX500Name.parse("O=BMW Dealership, L=New York, C=US");
}
