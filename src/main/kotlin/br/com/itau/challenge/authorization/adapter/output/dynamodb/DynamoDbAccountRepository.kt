package br.com.itau.challenge.authorization.adapter.output.dynamodb

import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.port.output.AccountRepository
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
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import java.math.BigDecimal

private const val ID_ATTRIBUTE = "accountId"
private const val OWNER_ATTRIBUTE = "owner"
private const val CREATED_AT_ATTRIBUTE = "createdAt"
private const val STATUS_ATTRIBUTE = "status"
private const val BALANCE_AMOUNT_ATTRIBUTE = "balanceAmount"
private const val BALANCE_CURRENCY_ATTRIBUTE = "balanceCurrency"
private const val VERSION_ATTRIBUTE = "version"

@Component
@CircuitBreaker(name = "dynamodb")
@Retry(name = "dynamodb")
class DynamoDbAccountRepository(
    private val dynamoDbClient: DynamoDbClient,
    @Value($$"${dynamodb.accounts-table-name}") private val tableName: String,
) : AccountRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun findById(accountId: String): Account? {
        logger.debug("Loading account {} from table {}", accountId, tableName)

        val request =
            GetItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(ID_ATTRIBUTE to accountId.toAttr()))
                .build()
        val response = dynamoDbClient.getItem(request)

        if (!response.hasItem()) {
            logger.debug("Account {} not found in table {}", accountId, tableName)
            return null
        }

        return response.item().toAccount()
    }

    override fun createIfAbsent(account: Account) {
        try {
            val request =
                PutItemRequest.builder()
                    .tableName(tableName)
                    .item(account.toItem())
                    .conditionExpression("attribute_not_exists($ID_ATTRIBUTE)")
                    .build()
            dynamoDbClient.putItem(request)
            logger.debug("Account {} created in table {}", account.id, tableName)
        } catch (_: ConditionalCheckFailedException) {
            logger.warn("Account {} already exists: skipping creation (idempotent)", account.id)
        }
    }

    override fun updateBalance(
        accountId: String,
        expectedVersion: Long,
        newBalance: Money,
    ): Boolean {
        return try {
            val request =
                UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(mapOf(ID_ATTRIBUTE to accountId.toAttr()))
                    .updateExpression("SET $BALANCE_AMOUNT_ATTRIBUTE = :newAmount, $BALANCE_CURRENCY_ATTRIBUTE = :newCurrency, $VERSION_ATTRIBUTE = :newVersion")
                    .conditionExpression("$VERSION_ATTRIBUTE = :expectedVersion")
                    .expressionAttributeValues(
                        mapOf(
                            ":newAmount" to AttributeValue.builder().n(newBalance.amount.toPlainString()).build(),
                            ":newCurrency" to newBalance.currency.toAttr(),
                            ":newVersion" to (expectedVersion + 1).toAttr(),
                            ":expectedVersion" to expectedVersion.toAttr(),
                        )
                    )
                    .build()
            dynamoDbClient.updateItem(request)
            logger.debug("Balance of account {} updated from version {}", accountId, expectedVersion)
            true
        } catch (_: ConditionalCheckFailedException) {
            logger.warn(
                "Conditional check failed while updating balance of account {}: expected version {} is stale",
                accountId,
                expectedVersion,
            )
            false
        }
    }

    private fun Account.toItem(): Map<String, AttributeValue> = mapOf(
        ID_ATTRIBUTE to id.toAttr(),
        OWNER_ATTRIBUTE to owner.toAttr(),
        CREATED_AT_ATTRIBUTE to createdAt.toAttr(),
        STATUS_ATTRIBUTE to status.toAttr(),
        BALANCE_AMOUNT_ATTRIBUTE to AttributeValue.builder().n(balance.amount.toPlainString()).build(),
        BALANCE_CURRENCY_ATTRIBUTE to balance.currency.toAttr(),
        VERSION_ATTRIBUTE to version.toAttr(),
    )

    private fun Map<String, AttributeValue>.toAccount(): Account = Account(
        id = getValue(ID_ATTRIBUTE).s(),
        owner = getValue(OWNER_ATTRIBUTE).s(),
        createdAt = getValue(CREATED_AT_ATTRIBUTE).n().toLong(),
        status = getValue(STATUS_ATTRIBUTE).s(),
        balance = Money(
            amount = BigDecimal(getValue(BALANCE_AMOUNT_ATTRIBUTE).n()),
            currency = getValue(BALANCE_CURRENCY_ATTRIBUTE).s(),
        ),
        version = getValue(VERSION_ATTRIBUTE).n().toLong(),
    )

    private fun String.toAttr(): AttributeValue = AttributeValue.builder().s(this).build()
    private fun Number.toAttr(): AttributeValue = AttributeValue.builder().n(this.toString()).build()
}