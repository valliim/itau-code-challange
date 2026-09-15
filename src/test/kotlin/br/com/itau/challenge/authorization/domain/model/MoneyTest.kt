package br.com.itau.challenge.authorization.domain.model

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MoneyTest {

    @Test
    fun `should expose the amount and currency it was created with`() {
        val money = Money(BigDecimal("10.50"), "BRL")

        assertEquals(BigDecimal("10.50"), money.amount)
        assertEquals("BRL", money.currency)
    }

    @Test
    fun `should be equal when amount and currency are equal`() {
        assertEquals(Money(BigDecimal("10.50"), "BRL"), Money(BigDecimal("10.50"), "BRL"))
    }

    @Test
    fun `should not be equal when currency differs`() {
        assertNotEquals(Money(BigDecimal("10.50"), "BRL"), Money(BigDecimal("10.50"), "USD"))
    }
}
