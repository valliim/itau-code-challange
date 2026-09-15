package br.com.itau.challenge.authorization.adapter.output.dynamodb

import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.Transaction
import br.com.itau.challenge.authorization.domain.model.TransactionStatus
import br.com.itau.challenge.authorization.domain.model.TransactionType
import br.com.itau.challenge.authorization.port.output.TransactionRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import java.math.BigDecimal
import java.time.OffsetDateTime

private const val ID_ATTRIBUTE = "transactionId"
private const val ACCOUNT_ID_ATTRIBUTE = "accountId"
private const val TYPE_ATTRIBUTE = "type"
private const val AMOUNT_ATTRIBUTE = "amount"
private const val CURRENCY_ATTRIBUTE = "currency"
private const val STATUS_ATTRIBUTE = "status"
private const val TIMESTAMP_ATTRIBUTE = "timestamp"

@Component
@CircuitBreaker(name = "dynamodb")
@Retry(name = "dynamodb")
class DynamoDbTransactionRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value($$"${dynamodb.transactions-table-name}") private val tableName: String,
) : TransactionRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun findById(transactionId: String): Transaction? {
        logger.debug("Loading transaction {} from table {}", transactionId, tableName)

        val request =
            GetItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(ID_ATTRIBUTE to transactionId.toAttr()))
                .build()
        val response = dynamoDbClient.getItem(request)

        if (!response.hasItem()) {
            logger.debug("Transaction {} not found in table {}", transactionId, tableName)
            return null
        }

        return response.item().toTransaction()
    }

    override fun save(transaction: Transaction) {
        try {
            val request =
                PutItemRequest.builder()
                    .tableName(tableName)
                    .item(transaction.toItem())
                    .conditionExpression("attribute_not_exists($ID_ATTRIBUTE)")
                    .build()
            dynamoDbClient.putItem(request)
            logger.debug("Transaction {} persisted with status {}", transaction.id, transaction.status)
        } catch (_: ConditionalCheckFailedException) {
            logger.warn("Transaction {} already persisted: skipping save (idempotent replay)", transaction.id)
        }
    }

    private fun Transaction.toItem(): Map<String, AttributeValue> = mapOf(
        ID_ATTRIBUTE to id.toAttr(),
        ACCOUNT_ID_ATTRIBUTE to accountId.toAttr(),
        TYPE_ATTRIBUTE to type.name.toAttr(),
        AMOUNT_ATTRIBUTE to AttributeValue.builder().n(amount.amount.toPlainString()).build(),
        CURRENCY_ATTRIBUTE to amount.currency.toAttr(),
        STATUS_ATTRIBUTE to status.name.toAttr(),
        TIMESTAMP_ATTRIBUTE to timestamp.toString().toAttr(),
    )

    private fun Map<String, AttributeValue>.toTransaction(): Transaction = Transaction(
        id = getValue(ID_ATTRIBUTE).s(),
        accountId = getValue(ACCOUNT_ID_ATTRIBUTE).s(),
        type = TransactionType.valueOf(getValue(TYPE_ATTRIBUTE).s()),
        amount = Money(
            amount = BigDecimal(getValue(AMOUNT_ATTRIBUTE).n()),
            currency = getValue(CURRENCY_ATTRIBUTE).s()
        ),
        status = TransactionStatus.valueOf(getValue(STATUS_ATTRIBUTE).s()),
        timestamp = OffsetDateTime.parse(getValue(TIMESTAMP_ATTRIBUTE).s()),
    )

    private fun String.toAttr(): AttributeValue = AttributeValue.builder().s(this).build()
}