package br.com.itau.challenge.authorization.domain.exception

import kotlin.test.Test
import kotlin.test.assertEquals

class ConcurrentBalanceUpdateExceptionTest {

    @Test
    fun `should carry a descriptive message including the account id`() {
        val exception = ConcurrentBalanceUpdateException("acc-1")

        assertEquals(
            "Could not update balance for account 'acc-1' due to concurrent modifications",
            exception.message,
        )
    }
}
