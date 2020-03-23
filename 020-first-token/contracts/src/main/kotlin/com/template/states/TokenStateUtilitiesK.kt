package com.template.states

/**
 * @receiver The states to tally.
 * @return The mapped sums of token quantities per issuer.
 */
fun Iterable<TokenStateK>.mapSumByIssuer() =
        /* Our tokens surely have repeated issuers, so we have more than 1 state per issuer. We still want to
         * file our tokens per issuer, so we are going to first create a Map(issuer -> list of quantities). */
        groupBy({ it.issuer }) { it.quantity }
                // Take the sum of quantities and replace in place.
                // Plus, we want to fail hard in case of overflow.
                .mapValues { it.value.reduce { sum, quantity -> Math.addExact(sum, quantity) } }
                // Make it immutable
                .toMap()
