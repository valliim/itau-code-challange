package br.com.itau.challenge.authorization.domain.exception

import kotlin.test.Test
import kotlin.test.assertEquals

class InvalidTransactionExceptionTest {

    @Test
    fun `should carry the given message`() {
        val exception = InvalidTransactionException("Transaction amount must be greater than zero")

        assertEquals("Transaction amount must be greater than zero", exception.message)
    }
}
