package br.com.itau.challenge.authorization.adapter.input.kafka

import br.com.itau.challenge.authorization.adapter.input.kafka.dto.AccountCreatedMessage
import br.com.itau.challenge.authorization.adapter.input.kafka.extension.toNewAccount
import br.com.itau.challenge.authorization.port.input.CreateAccountUseCase
import br.com.itau.challenge.authorization.metrics.MetricsInfo
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
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
    private val meterRegistry: MeterRegistry = Metrics.globalRegistry,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @KafkaListener(topics = [$$"${accounts.topic-name}"])
    fun consume(payload: String) {
        val message = try {
            objectMapper.readValue(payload, AccountCreatedMessage::class.java)
        } catch (exception: Exception) {
            meterRegistry.counter(
                MetricsInfo.KAFKA_ACCOUNT_EVENTS,
                MetricsInfo.RESULT_TAG,
                MetricsInfo.RESULT_INVALID,
            ).increment()
            logger.warn("Invalid account created event payload; forwarding to Kafka error handler", exception)
            throw InvalidAccountEventException(exception)
        }
        val newAccount = message.toNewAccount()

        MDC.putCloseable(ACCOUNT_ID_MDC_KEY, newAccount.id).use {
            try {
                createAccountUseCase.createAccount(newAccount)
                meterRegistry.counter(
                    MetricsInfo.KAFKA_ACCOUNT_EVENTS,
                    MetricsInfo.RESULT_TAG,
                    MetricsInfo.RESULT_PROCESSED,
                ).increment()
            } catch (exception: Exception) {
                meterRegistry.counter(
                    MetricsInfo.KAFKA_ACCOUNT_EVENTS,
                    MetricsInfo.RESULT_TAG,
                    MetricsInfo.RESULT_FAILED,
                ).increment()
                throw exception
            }
            logger.info("Account created event processed, with status {}", newAccount.status)
        }
    }
}