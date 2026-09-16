package br.com.itau.challenge.authorization.adapter.input.web

import br.com.itau.challenge.authorization.domain.exception.ConcurrentBalanceUpdateException
import br.com.itau.challenge.authorization.domain.model.AuthorizationResult
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.Transaction
import br.com.itau.challenge.authorization.domain.model.TransactionStatus
import br.com.itau.challenge.authorization.domain.model.TransactionType
import br.com.itau.challenge.authorization.port.input.AuthorizeTransactionUseCase
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import software.amazon.awssdk.core.exception.SdkException
import java.math.BigDecimal
import java.time.OffsetDateTime

private const val ACCOUNT_ID = "5b19c8b6-8cc4-4c72-8989-8c2ce15fa975"

private const val REQUEST_BODY =
    """{"account_id": "$ACCOUNT_ID", "type": "CREDIT", "amount": {"value": 97.07, "currency": "BRL"}}"""

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @MockitoBean
    private lateinit var authorizeTransactionUseCase: AuthorizeTransactionUseCase

    @Test
    fun `should return the succeeded transaction with the updated account balance`() {
        given(
            authorizeTransactionUseCase.authorize("tx-1", ACCOUNT_ID, TransactionType.CREDIT, Money(BigDecimal("97.07"), "BRL")),
        ).willReturn(
            AuthorizationResult(
                transaction =
                    Transaction("tx-1", ACCOUNT_ID, TransactionType.CREDIT, Money(BigDecimal("97.07"), "BRL"), TransactionStatus.SUCCEEDED, OffsetDateTime.now()),
                accountId = ACCOUNT_ID,
                balance = Money(BigDecimal("183.12"), "BRL"),
            ),
        )

        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = REQUEST_BODY
        }.andExpect {
            status { isOk() }
            jsonPath("$.transaction.id") { value("tx-1") }
            jsonPath("$.transaction.status") { value("SUCCEEDED") }
            jsonPath("$.account.balance.amount") { value(183.12) }
        }
    }

    @Test
    fun `should return the declined result produced by the application layer`() {
        given(
            authorizeTransactionUseCase.authorize("tx-1", ACCOUNT_ID, TransactionType.CREDIT, Money(BigDecimal("97.07"), "BRL")),
        ).willReturn(
            AuthorizationResult(
                transaction =
                    Transaction("tx-1", ACCOUNT_ID, TransactionType.CREDIT, Money(BigDecimal("97.07"), "BRL"), TransactionStatus.FAILED, OffsetDateTime.now()),
                accountId = ACCOUNT_ID,
                balance = Money(BigDecimal.ZERO, "BRL"),
            ),
        )

        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = REQUEST_BODY
        }.andExpect {
            status { isOk() }
            jsonPath("$.transaction.status") { value("FAILED") }
            jsonPath("$.account.id") { value(ACCOUNT_ID) }
            jsonPath("$.account.balance.amount") { value(0) }
        }
    }

    @Test
    fun `should reject an invalid transaction type`() {
        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"account_id": "$ACCOUNT_ID", "type": "INVALID", "amount": {"value": 10, "currency": "BRL"}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_TRANSACTION") }
            jsonPath("$.message") { value("Invalid transaction type: INVALID") }
            jsonPath("$.timestamp") { exists() }
        }
    }

    @Test
    fun `should reject an account id that is not a valid uuid`() {
        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"account_id": "not-a-uuid", "type": "CREDIT", "amount": {"value": 10, "currency": "BRL"}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
            jsonPath("$.message") { value(containsString("account_id must be a valid UUID")) }
        }

        verifyNoInteractions(authorizeTransactionUseCase)
    }

    @Test
    fun `should reject a blank transaction type`() {
        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"account_id": "$ACCOUNT_ID", "type": "   ", "amount": {"value": 10, "currency": "BRL"}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
            jsonPath("$.message") { value(containsString("type must not be blank")) }
        }

        verifyNoInteractions(authorizeTransactionUseCase)
    }

    @Test
    fun `should reject a non positive amount value`() {
        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"account_id": "$ACCOUNT_ID", "type": "CREDIT", "amount": {"value": 0, "currency": "BRL"}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
            jsonPath("$.message") { value(containsString("amount.value must be greater than zero")) }
        }

        verifyNoInteractions(authorizeTransactionUseCase)
    }

    @Test
    fun `should reject an invalid currency code`() {
        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"account_id": "$ACCOUNT_ID", "type": "CREDIT", "amount": {"value": 10, "currency": "brl"}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
            jsonPath("$.message") { value(containsString("amount.currency must be BRL")) }
        }

        verifyNoInteractions(authorizeTransactionUseCase)
    }

    @Test
    fun `should reject a currency other than BRL`() {
        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"account_id": "$ACCOUNT_ID", "type": "CREDIT", "amount": {"value": 10, "currency": "USD"}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
            jsonPath("$.message") { value(containsString("amount.currency must be BRL")) }
        }

        verifyNoInteractions(authorizeTransactionUseCase)
    }

    @Test
    fun `should reject a transaction id that exceeds the supported length`() {
        mockMvc.post("/transactions/${"x".repeat(101)}") {
            contentType = MediaType.APPLICATION_JSON
            content = REQUEST_BODY
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_ERROR") }
        }

        verifyNoInteractions(authorizeTransactionUseCase)
    }

    @Test
    fun `should reject a malformed json body`() {
        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"account_id": "$ACCOUNT_ID", "type": """
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MALFORMED_REQUEST") }
        }

        verifyNoInteractions(authorizeTransactionUseCase)
    }

    @Test
    fun `should reject a request without the amount object`() {
        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"account_id": "$ACCOUNT_ID", "type": "CREDIT"}"""
        }.andExpect {
            status { isBadRequest() }
        }

        verifyNoInteractions(authorizeTransactionUseCase)
    }

    @Test
    fun `should return conflict when the balance update retries are exhausted`() {
        given(
            authorizeTransactionUseCase.authorize("tx-1", ACCOUNT_ID, TransactionType.CREDIT, Money(BigDecimal("97.07"), "BRL")),
        ).willThrow(ConcurrentBalanceUpdateException(ACCOUNT_ID))

        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = REQUEST_BODY
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONCURRENT_BALANCE_UPDATE") }
        }
    }

    @Test
    fun `should return service unavailable when circuit breaker is open`() {
        val circuitBreaker = CircuitBreaker.ofDefaults("dynamodb")
        given(
            authorizeTransactionUseCase.authorize("tx-1", ACCOUNT_ID, TransactionType.CREDIT, Money(BigDecimal("97.07"), "BRL")),
        ).willThrow(CallNotPermittedException.createCallNotPermittedException(circuitBreaker))

        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = REQUEST_BODY
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
            jsonPath("$.message") { value("Persistence provider is unavailable") }
        }
    }

    @Test
    fun `should return service unavailable when the persistence provider fails`() {
        given(
            authorizeTransactionUseCase.authorize("tx-1", ACCOUNT_ID, TransactionType.CREDIT, Money(BigDecimal("97.07"), "BRL")),
        ).willThrow(SdkException.builder().message("connection refused").build())

        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = REQUEST_BODY
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("SERVICE_UNAVAILABLE") }
        }
    }

    @Test
    fun `should return internal server error for unexpected failures`() {
        given(
            authorizeTransactionUseCase.authorize("tx-1", ACCOUNT_ID, TransactionType.CREDIT, Money(BigDecimal("97.07"), "BRL")),
        ).willThrow(IllegalStateException("boom"))

        mockMvc.post("/transactions/tx-1") {
            contentType = MediaType.APPLICATION_JSON
            content = REQUEST_BODY
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
            jsonPath("$.message") { value("Unexpected internal error") }
        }
    }
}
