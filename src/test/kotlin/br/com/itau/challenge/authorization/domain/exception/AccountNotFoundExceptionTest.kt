package br.com.itau.challenge.authorization.domain.exception

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountNotFoundExceptionTest {

    @Test
    fun `should carry a descriptive message including the account id`() {
        val exception = AccountNotFoundException("acc-1")

        assertEquals("Account not found: acc-1", exception.message)
    }
}
