package br.com.itau.challenge.authorization.adapter.input.web

import br.com.itau.challenge.authorization.domain.exception.ConcurrentBalanceUpdateException
import br.com.itau.challenge.authorization.domain.exception.InvalidTransactionException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `should map a concurrent balance update to conflict`() {
        val response = handler.handleConcurrentBalanceUpdate(ConcurrentBalanceUpdateException("account-1"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.code).isEqualTo("CONCURRENT_BALANCE_UPDATE")
        assertThat(response.body?.message).contains("account-1")
        assertThat(response.body?.timestamp).isNotNull()
    }

    @Test
    fun `should map an invalid transaction to bad request`() {
        val response = handler.handleInvalidTransaction(InvalidTransactionException("Invalid transaction type: X"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo("INVALID_TRANSACTION")
        assertThat(response.body?.message).isEqualTo("Invalid transaction type: X")
    }

    @Test
    fun `should map an unreadable body to bad request`() {
        val inputMessage = MockHttpInputMessage("{".toByteArray())
        val response = handler.handleUnreadableBody(HttpMessageNotReadableException("broken", inputMessage))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo("MALFORMED_REQUEST")
    }

    @Test
    fun `should map an unexpected failure to internal server error`() {
        val response = handler.handleUnexpected(IllegalStateException("boom"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(response.body?.code).isEqualTo("INTERNAL_ERROR")
        assertThat(response.body?.message).isEqualTo("Unexpected internal error")
    }

    @Test
    fun `should fall back to a generic message when there are no field errors`() {
        val target = Any()
        val bindingResult = BeanPropertyBindingResult(target, "transactionRequest")
        val methodParameter = MethodParameter(GlobalExceptionHandlerTest::class.java.getDeclaredMethod("validationTarget", String::class.java), 0)

        val response = handler.handleValidation(MethodArgumentNotValidException(methodParameter, bindingResult))

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo("VALIDATION_ERROR")
        assertThat(response.body?.message).isEqualTo("Invalid request payload")
    }

    @Test
    fun `should aggregate field errors into the validation message`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "transactionRequest")
        bindingResult.addError(FieldError("transactionRequest", "account_id", "account_id must be a valid UUID"))
        val methodParameter = MethodParameter(GlobalExceptionHandlerTest::class.java.getDeclaredMethod("validationTarget", String::class.java), 0)

        val response = handler.handleValidation(MethodArgumentNotValidException(methodParameter, bindingResult))

        assertThat(response.body?.message).isEqualTo("account_id: account_id must be a valid UUID")
    }

    @Test
    fun `should default the field message to an empty string when a field error has no default message`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "transactionRequest")
        bindingResult.addError(FieldError("transactionRequest", "account_id", null, false, null, null, null))
        val methodParameter = MethodParameter(GlobalExceptionHandlerTest::class.java.getDeclaredMethod("validationTarget", String::class.java), 0)

        val response = handler.handleValidation(MethodArgumentNotValidException(methodParameter, bindingResult))

        assertThat(response.body?.message).isEqualTo("account_id: ")
    }

    @Test
    fun `should default the message to an empty string when an invalid transaction has no message`() {
        val exception = mock(InvalidTransactionException::class.java)
        given(exception.message).willReturn(null)

        val response = handler.handleInvalidTransaction(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.code).isEqualTo("INVALID_TRANSACTION")
        assertThat(response.body?.message).isEqualTo("")
    }

    @Test
    fun `should default the message to an empty string when a concurrent balance update has no message`() {
        val exception = mock(ConcurrentBalanceUpdateException::class.java)
        given(exception.message).willReturn(null)

        val response = handler.handleConcurrentBalanceUpdate(exception)

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.code).isEqualTo("CONCURRENT_BALANCE_UPDATE")
        assertThat(response.body?.message).isEqualTo("")
    }

    @Suppress("UNUSED_PARAMETER", "unused")
    private fun validationTarget(value: String) = Unit
}
