package br.com.itau.challenge.authorization.adapter.input.kafka

import br.com.itau.challenge.authorization.domain.model.NewAccount
import br.com.itau.challenge.authorization.port.input.CreateAccountUseCase
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountCreatedConsumerTest {

    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun `should deserialize the JSON payload and delegate it to the create account use case`() {
        val createdAccounts = mutableListOf<NewAccount>()
        val useCase = CreateAccountUseCase { createdAccounts.add(it) }
        val consumer = AccountCreatedConsumer(useCase, objectMapper)

        consumer.consume(
            """{"account": {"id": "acc-1", "owner": "owner-1", "created_at": 1634874339000000, "status": "ENABLED"}}""",
        )

        assertEquals(
            listOf(NewAccount(id = "acc-1", owner = "owner-1", createdAt = 1634874339000000L, status = "ENABLED")),
            createdAccounts,
        )
    }

    @Test
    fun `should discard the event and keep consuming when the payload is invalid`() {
        val createdAccounts = mutableListOf<NewAccount>()
        val useCase = CreateAccountUseCase { createdAccounts.add(it) }
        val consumer = AccountCreatedConsumer(useCase, objectMapper)

        consumer.consume("not-a-valid-json-payload")

        assertEquals(emptyList(), createdAccounts)
    }

    @Test
    fun `should propagate the exception when the use case fails while processing the event`() {
        val useCase = CreateAccountUseCase { throw IllegalStateException("boom") }
        val consumer = AccountCreatedConsumer(useCase, objectMapper)

        assertFailsWith<IllegalStateException> {
            consumer.consume(
                """{"account": {"id": "acc-1", "owner": "owner-1", "created_at": 1634874339000000, "status": "ENABLED"}}""",
            )
        }
    }
}
