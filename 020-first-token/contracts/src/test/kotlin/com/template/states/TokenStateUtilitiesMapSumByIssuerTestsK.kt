package com.template.states

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals

class TokenStateUtilitiesMapSumByIssuerTestsK {

    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val bob = TestIdentity(CordaX500Name("Bob", "London", "GB")).party
    private val carly = TestIdentity(CordaX500Name("Carly", "London", "GB")).party

    @Test
    fun `mapSumByIssuer gets same value on singleton`() {
        val mappedSums = listOf(TokenStateK(alice, bob, 10)).mapSumByIssuer()
        assertEquals(1, mappedSums.size)
        assertEquals(10L, mappedSums[alice])
    }

    @Test
    fun `mapSumByIssuer gets sum on unique issuer`() {
        val mappedSums = listOf(
                TokenStateK(alice, bob, 10),
                TokenStateK(alice, carly, 15))
                .mapSumByIssuer()
        assertEquals(1, mappedSums.size)
        assertEquals(25L, mappedSums[alice])
    }

    @Test
    fun `mapSumByIssuer gets sum for each issuer`() {
        val mappedSums = listOf(
                TokenStateK(alice, bob, 10),
                TokenStateK(alice, carly, 15),
                TokenStateK(carly, bob, 30),
                TokenStateK(carly, carly, 25),
                TokenStateK(carly, alice, 2)
        )
                .mapSumByIssuer()
        assertEquals(2, mappedSums.size)
        assertEquals(25L, mappedSums[alice])
        assertEquals(57L, mappedSums[carly])
    }

    @Test(expected = ArithmeticException::class)
    fun `overflow triggers error in mapSumByIssuer`() {
        listOf(TokenStateK(alice, bob, Long.MAX_VALUE),
                TokenStateK(alice, carly, 1))
                .mapSumByIssuer()
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `mapSumByIssuer is immutable`() {
        val mappedSums = listOf(TokenStateK(alice, bob, 10)).mapSumByIssuer() as MutableMap
        mappedSums[alice] = 20L
    }
}