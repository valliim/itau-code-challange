package br.com.itau.challenge.authorization

import br.com.itau.challenge.authorization.port.output.AccountRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

private const val ACCOUNT_CREATION_TIMEOUT_MILLIS = 30_000L
private const val POLL_INTERVAL_MILLIS = 250L

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(
    properties = [
        "dynamodb.endpoint=http://localhost:8000",
        "dynamodb.use-static-credentials=true",
        $$"spring.kafka.consumer.group-id=authorization-e2e-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
    ],
)
class AuthorizationEndToEndIntegrationTest {

    @Value($$"${accounts.topic-name}")
    private lateinit var topic: String

    @Value($$"${dynamodb.accounts-table-name}")
    private lateinit var accountsTableName: String

    @Value($$"${dynamodb.transactions-table-name}")
    private lateinit var transactionsTableName: String

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var dynamoDbClient: DynamoDbClient

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val accountId = UUID.randomUUID().toString()
    private val createdTransactionIds = mutableListOf<String>()

    @AfterEach
    fun tearDown() {
        dynamoDbClient.deleteItem(
            DeleteItemRequest
                .builder()
                .tableName(accountsTableName)
                .key(mapOf("accountId" to AttributeValue.builder().s(accountId).build()))
                .build(),
        )
        createdTransactionIds.forEach { transactionId ->
            dynamoDbClient.deleteItem(
                DeleteItemRequest
                    .builder()
                    .tableName(transactionsTableName)
                    .key(mapOf("transactionId" to AttributeValue.builder().s(transactionId).build()))
                    .build(),
            )
        }
    }

    @Test
    fun `should create the account from Kafka and then authorize transactions over HTTP`() {
        publishAccountCreatedEvent()
        awaitAccountCreation()

        val creditId = authorizeSucceeding(type = "CREDIT", value = "100.00", expectedBalance = "100.00")
        val debitId = authorizeSucceeding(type = "DEBIT", value = "30.00", expectedBalance = "70.00")

        
        val declinedId = post(type = "DEBIT", value = "500.00").let { (status, body) ->
            assertEquals(
                expected = HttpStatus.OK,
                actual = status,
                message = "declined debit must use the regular contract"
            )
            val json = objectMapper.readTree(body)
            assertEquals(
                expected = "FAILED",
                actual = json.path("transaction").path("status").asText(),
                message = "declined debit status"
            )
            assertEquals(
                expected = 0,
                actual = BigDecimal("70.00").compareTo(BigDecimal(json.path("account").path("balance").path("amount").asText())),
                message = "balance must not change on a declined debit",
            )
            json.path("transaction").path("id").asText()
        }

        val (replayStatus, replayBody) = post(type = "CREDIT", value = "100.00", transactionId = creditId)
        assertEquals(
            expected = HttpStatus.OK,
            actual = replayStatus,
            message = "replay HTTP status"
        )

        val replayJson = objectMapper.readTree(replayBody)
        assertEquals(
            expected = creditId,
            actual = replayJson.path("transaction").path("id").asText(),
            message = "replayed transaction id"
        )
        assertEquals(
            expected = "SUCCEEDED",
            actual = replayJson.path("transaction").path("status").asText(),
            message = "replayed status"
        )
        assertEquals(
            expected = 0,
            actual = BigDecimal("70.00").compareTo(BigDecimal(replayJson.path("account").path("balance").path("amount").asText())),
            message = "replay must not apply the transaction twice",
        )

        val (invalidStatus, invalidBody) = post(type = "TRANSFER", value = "10.00")
        assertEquals(
            expected = HttpStatus.BAD_REQUEST,
            actual = invalidStatus,
            message = "invalid type HTTP status"
        )
        assertEquals(
            expected = "INVALID_TRANSACTION",
            actual = objectMapper.readTree(invalidBody).path("code").asText(),
            message = "invalid type error code",
        )

        val account = assertNotNull(
            actual = accountRepository.findById(accountId),
            message = "account must still exist"
        )
        assertEquals(
            expected = BigDecimal("70.00"),
            actual = account.balance.amount.stripTrailingZeros().setScale(2),
            message = "final balance"
        )
        assertEquals(
            expected = "BRL",
            actual = account.balance.currency,
            message = "final balance currency"
        )
        assertEquals(
            expected = listOf(creditId, debitId, declinedId).distinct().size,
            actual = 3,
            message = "transaction ids must be unique"
        )
    }

    private fun publishAccountCreatedEvent() {
        kafkaTemplate
            .send(
                topic,
                accountId,
                """{"account": {"id": "$accountId", "owner": "${UUID.randomUUID()}", """ +
                    """"created_at": 1634874339000000, "status": "ENABLED"}}""",
            ).get(10, TimeUnit.SECONDS)
    }

    private fun awaitAccountCreation() {
        val deadline = System.currentTimeMillis() + ACCOUNT_CREATION_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val account = accountRepository.findById(accountId)
            if (account != null) {
                assertEquals(
                    expected = BigDecimal.ZERO,
                    actual = account.balance.amount.stripTrailingZeros(),
                    message = "initial balance"
                )
                return
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        fail("account $accountId was not created from the Kafka event within $ACCOUNT_CREATION_TIMEOUT_MILLIS ms")
    }

    private fun authorizeSucceeding(
        type: String,
        value: String,
        expectedBalance: String,
    ): String {
        val (status, body) = post(type = type, value = value)
        assertEquals(HttpStatus.OK, status, "$type HTTP status")

        val json = objectMapper.readTree(body)
        assertEquals(
            expected = "SUCCEEDED",
            actual = json.path("transaction").path("status").asText(),
            message = "$type status"
        )
        assertEquals(
            expected = type,
            actual = json.path("transaction").path("type").asText(),
            message = "$type transaction type"
        )
        assertEquals(
            expected = accountId,
            actual = json.path("account").path("id").asText(),
            message = "$type account id"
        )
        assertEquals(
            expected = 0,
            actual = BigDecimal(expectedBalance).compareTo(
                BigDecimal(
                    json
                        .path("account")
                        .path("balance")
                        .path("amount")
                        .asText()
                )
            ),
            message = "$type resulting balance",
        )
        return json.path("transaction").path("id").asText()
    }

    private fun post(
        type: String,
        value: String,
        transactionId: String = UUID.randomUUID().toString(),
    ): Pair<HttpStatus, String> {
        createdTransactionIds += transactionId
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val payload =
            """{"account_id": "$accountId", "type": "$type", "amount": {"value": $value, "currency": "BRL"}}"""

        val response =
            restTemplate.postForEntity(
                "/transactions/$transactionId",
                HttpEntity(payload, headers),
                String::class.java,
            )

        return HttpStatus.valueOf(response.statusCode.value()) to response.body.orEmpty()
    }
}
