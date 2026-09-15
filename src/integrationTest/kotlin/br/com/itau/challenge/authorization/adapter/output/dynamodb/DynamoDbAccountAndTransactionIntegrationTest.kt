package br.com.itau.challenge.authorization.adapter.output.dynamodb

import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.Transaction
import br.com.itau.challenge.authorization.domain.model.TransactionStatus
import br.com.itau.challenge.authorization.domain.model.TransactionType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import java.math.BigDecimal
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamoDbAccountAndTransactionIntegrationTest {

    private val accountsTableName = System.getenv("ACCOUNTS_TABLE_NAME") ?: "Accounts"
    private val transactionsTableName = System.getenv("TRANSACTIONS_TABLE_NAME") ?: "Transactions"

    private val dynamoDbClient: DynamoDbClient =
        DynamoDbClient
            .builder()
            .endpointOverride(URI.create(System.getenv("DYNAMODB_ENDPOINT") ?: "http://localhost:8000"))
            .region(Region.of(System.getenv("DYNAMODB_REGION") ?: "us-east-1"))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
            .build()

    private val accountRepository = DynamoDbAccountRepository(dynamoDbClient, accountsTableName)
    private val transactionRepository = DynamoDbTransactionRepository(dynamoDbClient, transactionsTableName)

    private lateinit var accountId: String
    private lateinit var transactionId: String

    @BeforeEach
    fun setUp() {
        accountId = "integration-test-${UUID.randomUUID()}"
        transactionId = "integration-test-${UUID.randomUUID()}"
    }

    @AfterEach
    fun tearDown() {
        dynamoDbClient.deleteItem(
            DeleteItemRequest
                .builder()
                .tableName(accountsTableName)
                .key(mapOf("accountId" to AttributeValue.builder().s(accountId).build()))
                .build(),
        )
        dynamoDbClient.deleteItem(
            DeleteItemRequest
                .builder()
                .tableName(transactionsTableName)
                .key(mapOf("transactionId" to AttributeValue.builder().s(transactionId).build()))
                .build(),
        )
    }

    @Test
    fun `should create an account and read it back from the real table`() {
        accountRepository.createIfAbsent(Account(accountId, "owner-1", 1L, "ENABLED", Money(BigDecimal.ZERO, "BRL")))

        val account = accountRepository.findById(accountId)

        assertEquals(accountId, account?.id)
        assertEquals(BigDecimal.ZERO, account?.balance?.amount)
    }

    @Test
    fun `should not overwrite the balance when creating the same account twice`() {
        accountRepository.createIfAbsent(Account(accountId, "owner-1", 1L, "ENABLED", Money(BigDecimal.ZERO, "BRL")))
        accountRepository.updateBalance(accountId, 0, Money(BigDecimal(100), "BRL"))

        accountRepository.createIfAbsent(Account(accountId, "owner-1", 1L, "ENABLED", Money(BigDecimal(999), "BRL")))

        assertEquals(BigDecimal(100), accountRepository.findById(accountId)?.balance?.amount)
    }

    @Test
    fun `should update the balance only when the version matches`() {
        accountRepository.createIfAbsent(Account(accountId, "owner-1", 1L, "ENABLED", Money(BigDecimal.ZERO, "BRL")))

        val updated = accountRepository.updateBalance(accountId, 0, Money(BigDecimal(50), "BRL"))
        val staleUpdate = accountRepository.updateBalance(accountId, 0, Money(BigDecimal(999), "BRL"))

        assertTrue(updated)
        assertTrue(!staleUpdate)
        assertEquals(BigDecimal(50), accountRepository.findById(accountId)?.balance?.amount)
    }

    @Test
    fun `should save a transaction and read it back, ignoring a duplicate save`() {
        val transaction = Transaction(transactionId, accountId, TransactionType.CREDIT, Money(BigDecimal(10), "BRL"), TransactionStatus.SUCCEEDED, OffsetDateTime.now())

        transactionRepository.save(transaction)
        transactionRepository.save(transaction.copy(status = TransactionStatus.FAILED))

        val persisted = transactionRepository.findById(transactionId)
        assertEquals(TransactionStatus.SUCCEEDED, persisted?.status)
    }

    @Test
    fun `should return null when the transaction is not found`() {
        assertNull(transactionRepository.findById("does-not-exist-${UUID.randomUUID()}"))
    }
}
