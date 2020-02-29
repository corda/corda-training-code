package com.template.states

import net.corda.core.internal.toMultiMap

fun Collection<TokenStateK>.mapSumByIssuer() = map { it.issuer to it.quantity }
        .toMultiMap()
        .mapValues { it.value.reduce { sum, quantity -> Math.addExact(sum, quantity) } }
        .toMap()
