package com.template.states

fun Iterable<TokenStateK>.mapSumByIssuer() = groupBy({ it.issuer }) { it.quantity }
        .mapValues { it.value.reduce { sum, quantity -> Math.addExact(sum, quantity) } }
        .toMap()
