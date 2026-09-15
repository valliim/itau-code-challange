package br.com.itau.challenge.authorization.adapter.input.kafka

import br.com.itau.challenge.authorization.adapter.input.kafka.dto.AccountCreatedMessage
import br.com.itau.challenge.authorization.adapter.input.kafka.extension.toNewAccount
import br.com.itau.challenge.authorization.port.input.CreateAccountUseCase
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private const val ACCOUNT_ID_MDC_KEY = "accountId"

@Component
class AccountCreatedConsumer(
    private val createAccountUseCase: CreateAccountUseCase,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @KafkaListener(topics = [$$"${accounts.topic-name}"])
    fun consume(payload: String) {
        val message = runCatching {
            objectMapper.readValue(payload, AccountCreatedMessage::class.java)
        }.getOrElse { ex ->
            logger.error("Discarding account created event with invalid payload. Payload: {}", payload, ex)
            return
        }
        val newAccount = message.toNewAccount()

        MDC.putCloseable(ACCOUNT_ID_MDC_KEY, newAccount.id).use {
            createAccountUseCase.createAccount(newAccount)
            logger.info("Account created event processed, with status {}", newAccount.status)
        }
    }
}