package br.com.itau.challenge.authorization.adapter.output.dynamodb

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class DynamoDbResilienceTest {

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    private lateinit var retryRegistry: RetryRegistry

    @Autowired
    private lateinit var accountRepository: DynamoDbAccountRepository

    @Autowired
    private lateinit var transactionRepository: DynamoDbTransactionRepository

    @MockitoBean
    private lateinit var dynamoDbClient: DynamoDbClient

    @Test
    fun `should configure dynamodb circuit breaker with required properties`() {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("dynamodb")
        val config = circuitBreaker.circuitBreakerConfig

        assertEquals(10, config.slidingWindowSize)
        assertEquals(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, config.slidingWindowType)
        assertEquals(5, config.minimumNumberOfCalls)
        assertEquals(50.0f, config.failureRateThreshold)
        assertEquals(3, config.permittedNumberOfCallsInHalfOpenState)
        assertEquals(10000L, config.waitIntervalFunctionInOpenState.apply(1))
        assertTrue(config.recordExceptionPredicate.test(SdkException.builder().message("err").build()))
        assertTrue(config.recordExceptionPredicate.test(IOException("socket closed")))
        assertTrue(config.ignoreExceptionPredicate.test(ConditionalCheckFailedException.builder().message("err").build()))
    }

    @Test
    fun `should configure dynamodb retry with required properties`() {
        val retry = retryRegistry.retry("dynamodb")
        val config = retry.retryConfig

        assertEquals(3, config.maxAttempts)
        assertTrue(config.exceptionPredicate.test(SdkException.builder().message("err").build()))
        assertTrue(config.exceptionPredicate.test(IOException("socket timeout")))
        assertTrue(!config.exceptionPredicate.test(ConditionalCheckFailedException.builder().message("err").build()))
    }

    @Test
    fun `should transition circuit breaker from closed to open to half open to closed`() {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("dynamodb")
        circuitBreaker.reset()
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)

        // ConditionalCheckFailedException should be ignored and not increment failure rate
        for (i in 1..5) {
            circuitBreaker.onError(
                0,
                java.util.concurrent.TimeUnit.MILLISECONDS,
                ConditionalCheckFailedException.builder().message("conflict").build(),
            )
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)

        // 5 SdkExceptions should breach the 50% threshold on minimum 5 calls and transition to OPEN
        for (i in 1..5) {
            circuitBreaker.onError(
                0,
                java.util.concurrent.TimeUnit.MILLISECONDS,
                SdkException.builder().message("dynamo failure").build(),
            )
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)

        // While OPEN, calls are not permitted
        assertThrows<CallNotPermittedException> {
            circuitBreaker.acquirePermission()
        }

        // Transition to HALF_OPEN
        circuitBreaker.transitionToHalfOpenState()
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.state)

        // 3 successful calls in HALF_OPEN should close the circuit
        for (i in 1..3) {
            circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    fun `should block repository calls when circuit breaker is open`() {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("dynamodb")
        circuitBreaker.reset()
        circuitBreaker.transitionToOpenState()

        try {
            assertThrows<CallNotPermittedException> {
                accountRepository.findById("acc-1")
            }
            assertThrows<CallNotPermittedException> {
                transactionRepository.findById("tx-1")
            }
        } finally {
            circuitBreaker.reset()
        }
    }

    @Test
    fun `should execute repository calls and retry when circuit is closed`() {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("dynamodb")
        circuitBreaker.reset()

        given(dynamoDbClient.getItem(any(GetItemRequest::class.java)))
            .willReturn(
                GetItemResponse.builder()
                    .item(
                        mapOf(
                            "accountId" to AttributeValue.builder().s("acc-1").build(),
                            "owner" to AttributeValue.builder().s("owner-1").build(),
                            "createdAt" to AttributeValue.builder().n("1").build(),
                            "status" to AttributeValue.builder().s("ENABLED").build(),
                            "balanceAmount" to AttributeValue.builder().n("50.00").build(),
                            "balanceCurrency" to AttributeValue.builder().s("BRL").build(),
                            "version" to AttributeValue.builder().n("1").build(),
                        ),
                    ).build(),
            )

        val account = accountRepository.findById("acc-1")
        assertEquals("acc-1", account?.id)
        verify(dynamoDbClient).getItem(any(GetItemRequest::class.java))
    }
}
