package br.com.itau.challenge.authorization.adapter.output.dynamodb

import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.Transaction
import br.com.itau.challenge.authorization.domain.model.TransactionStatus
import br.com.itau.challenge.authorization.domain.model.TransactionType
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DynamoDbTransactionRepositoryTest {

    @Test
    fun `should return null when the transaction does not exist`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.getItem(any(GetItemRequest::class.java))).willReturn(GetItemResponse.builder().build())
        val repository = DynamoDbTransactionRepository(client, "Transactions")

        assertNull(repository.findById("missing"))
    }

    @Test
    fun `should map a found item back into a transaction`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.getItem(any(GetItemRequest::class.java))).willReturn(
            GetItemResponse.builder().item(itemOf("tx-1")).build(),
        )
        val repository = DynamoDbTransactionRepository(client, "Transactions")

        val transaction = repository.findById("tx-1")

        assertEquals("tx-1", transaction?.id)
        assertEquals(TransactionType.CREDIT, transaction?.type)
        assertEquals(TransactionStatus.SUCCEEDED, transaction?.status)
    }

    @Test
    fun `should save the transaction into the configured table`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.putItem(any(PutItemRequest::class.java))).willReturn(PutItemResponse.builder().build())
        val repository = DynamoDbTransactionRepository(client, "Transactions")

        repository.save(
            Transaction("tx-1", "acc-1", TransactionType.CREDIT, Money(BigDecimal(10), "BRL"), TransactionStatus.SUCCEEDED, OffsetDateTime.now()),
        )

        val requestCaptor = ArgumentCaptor.forClass(PutItemRequest::class.java)
        verify(client).putItem(requestCaptor.capture())
        assertEquals("Transactions", requestCaptor.value.tableName())
    }

    @Test
    fun `should silently ignore saving when the transaction id was already persisted`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(ConditionalCheckFailedException.builder().message("exists").build())
        val repository = DynamoDbTransactionRepository(client, "Transactions")

        repository.save(
            Transaction("tx-1", "acc-1", TransactionType.CREDIT, Money(BigDecimal(10), "BRL"), TransactionStatus.SUCCEEDED, OffsetDateTime.now()),
        )
    }

    private fun itemOf(id: String): Map<String, AttributeValue> =
        mapOf(
            "transactionId" to AttributeValue.builder().s(id).build(),
            "accountId" to AttributeValue.builder().s("acc-1").build(),
            "type" to AttributeValue.builder().s("CREDIT").build(),
            "amount" to AttributeValue.builder().n("10.00").build(),
            "currency" to AttributeValue.builder().s("BRL").build(),
            "status" to AttributeValue.builder().s("SUCCEEDED").build(),
            "timestamp" to AttributeValue.builder().s(OffsetDateTime.parse("2025-07-08T15:57:55-03:00").toString()).build(),
        )
}
