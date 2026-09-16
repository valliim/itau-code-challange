package br.com.itau.challenge.authorization.adapter.input.kafka

import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.port.output.AccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.fail

@SpringBootTest
@TestPropertySource(
    properties = [
        "dynamodb.endpoint=http://localhost:8000",
        "dynamodb.use-static-credentials=true",
        $$"spring.kafka.consumer.group-id=account-created-consumer-it-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
    ],
)
class AccountCreatedConsumerIntegrationTest {

    @Value($$"${accounts.topic-name}")
    private lateinit var topic: String

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockitoBean
    private lateinit var accountRepository: AccountRepository

    @Test
    fun `should consume a message published to the real topic via the real application listener`() {
        val accountId = "it-${UUID.randomUUID()}"
        val ownerId = UUID.randomUUID().toString()

        kafkaTemplate.send(
            topic,
            accountId,
            """{"account": {"id": "$accountId", "owner": "$ownerId", "created_at": 1634874339000000, "status": "ENABLED"}}""",
        ).get(5, TimeUnit.SECONDS)

        val consumed = awaitConsumedAccounts(accountId, minCount = 1).single()
        assertEquals(accountId, consumed.id, "account id")
        assertEquals(ownerId, consumed.owner, "account owner")
        assertEquals(1634874339000000, consumed.createdAt, "account createdAt")
        assertEquals("ENABLED", consumed.status, "account status")
        assertEquals(Money(BigDecimal.ZERO, "BRL"), consumed.balance, "initial balance")
        assertEquals(0L, consumed.version, "initial version")
    }

    @Test
    fun `should skip a malformed payload and keep consuming the next valid message on the same partition`() {
        val accountId = "it-${UUID.randomUUID()}"
        val ownerId = UUID.randomUUID().toString()

        kafkaTemplate.send(topic, accountId, """{"account": {"id": "$accountId", """)
            .get(5, TimeUnit.SECONDS)

        kafkaTemplate.send(
            topic,
            accountId,
            """{"account": {"id": "$accountId", "owner": "$ownerId", "created_at": 1634874339000000, "status": "ENABLED"}}""",
        ).get(5, TimeUnit.SECONDS)

        val consumed = awaitConsumedAccounts(accountId, minCount = 1)
        assertEquals(1, consumed.size, "only the valid message must reach the repository")
        assertEquals(ownerId, consumed.single().owner, "account owner")
    }

    @Test
    fun `should handle duplicate events by delegating each delivery to the repository with equivalent data`() {
        val accountId = "it-${UUID.randomUUID()}"
        val ownerId = UUID.randomUUID().toString()
        val payload =
            """{"account": {"id": "$accountId", "owner": "$ownerId", "created_at": 1634874339000000, "status": "ENABLED"}}"""

        repeat(2) {
            kafkaTemplate.send(topic, accountId, payload).get(5, TimeUnit.SECONDS)
        }

        val consumed = awaitConsumedAccounts(accountId, minCount = 2)
        assertEquals(2, consumed.size, "both deliveries must reach the repository")
        assertEquals(
            listOf(
                Account(
                    id = accountId,
                    owner = ownerId,
                    createdAt = 1634874339000000,
                    status = "ENABLED",
                    balance = Money(BigDecimal.ZERO, "BRL"),
                    version = 0,
                ),
            ),
            consumed.distinct(),
            "every delivery must carry the same account data",
        )
    }

    private fun awaitConsumedAccounts(
        accountId: String,
        minCount: Int = 1,
        timeoutMillis: Long = 60_000L,
    ): List<Account> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val matches = mockingDetails(accountRepository)
                .invocations
                .filter { it.method.name == "createIfAbsent" && it.arguments.isNotEmpty() }
                .mapNotNull { it.arguments[0] as? Account }
                .filter { it.id == accountId }
            if (matches.size >= minCount) {
                return matches
            }
            Thread.sleep(100)
        }
        val matches = mockingDetails(accountRepository)
            .invocations
            .filter { it.method.name == "createIfAbsent" && it.arguments.isNotEmpty() }
            .mapNotNull { it.arguments[0] as? Account }
            .filter { it.id == accountId }
        if (matches.size >= minCount) return matches
        fail("Expected at least $minCount consumed accounts with id $accountId, but found ${matches.size} within $timeoutMillis ms")
    }
}
