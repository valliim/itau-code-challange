package br.com.itau.challenge.authorization.adapter.input.kafka.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.util.backoff.FixedBackOff
import br.com.itau.challenge.authorization.adapter.input.kafka.InvalidAccountEventException

@Configuration
class KafkaConsumerConfig(
    @Value($$"${accounts.topic-name}")
    private val accountsTopic: String,
    @Value($$"${accounts.dead-letter-suffix}")
    private val deadLetterSuffix: String,
    @Value($$"${accounts.retry-interval-millis}")
    private val retryIntervalMillis: Long,
    @Value($$"${accounts.max-retries}")
    private val maxRetries: Long,
    @Value($$"${accounts.dead-letter-partitions}")
    private val deadLetterPartitions: Int,
    @Value($$"${accounts.dead-letter-replication-factor}")
    private val deadLetterReplicationFactor: Int,
) {

    @Bean
    fun accountCreatedDeadLetterTopic(): NewTopic =
        TopicBuilder
            .name(accountsTopic + deadLetterSuffix)
            .partitions(deadLetterPartitions)
            .replicas(deadLetterReplicationFactor)
            .build()

    @Bean
    fun kafkaErrorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate)
        return DefaultErrorHandler(
            recoverer,
            FixedBackOff(retryIntervalMillis, maxRetries),
        ).apply { addNotRetryableExceptions(InvalidAccountEventException::class.java) }
    }
}
