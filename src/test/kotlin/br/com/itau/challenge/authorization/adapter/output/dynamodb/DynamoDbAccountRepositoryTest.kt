package br.com.itau.challenge.authorization.adapter.output.dynamodb

import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.Money
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
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamoDbAccountRepositoryTest {

    @Test
    fun `should return null when the account does not exist`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.getItem(any(GetItemRequest::class.java))).willReturn(GetItemResponse.builder().build())
        val repository = DynamoDbAccountRepository(client, "Accounts")

        assertNull(repository.findById("missing"))
    }

    @Test
    fun `should map a found item back into an account`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.getItem(any(GetItemRequest::class.java))).willReturn(
            GetItemResponse.builder().item(itemOf(id = "acc-1", balanceAmount = "150.50", version = 3)).build(),
        )
        val repository = DynamoDbAccountRepository(client, "Accounts")

        val account = repository.findById("acc-1")

        assertEquals("acc-1", account?.id)
        assertEquals(BigDecimal("150.50"), account?.balance?.amount)
        assertEquals(3L, account?.version)
    }

    @Test
    fun `should create the account when it does not exist yet`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.putItem(any(PutItemRequest::class.java))).willReturn(PutItemResponse.builder().build())
        val repository = DynamoDbAccountRepository(client, "Accounts")

        repository.createIfAbsent(Account("acc-1", "owner-1", 1L, "ENABLED", Money(BigDecimal.ZERO, "BRL")))

        val requestCaptor = ArgumentCaptor.forClass(PutItemRequest::class.java)
        verify(client).putItem(requestCaptor.capture())
        assertEquals("Accounts", requestCaptor.value.tableName())
    }

    @Test
    fun `should silently ignore creation when the account already exists`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.putItem(any(PutItemRequest::class.java)))
            .willThrow(ConditionalCheckFailedException.builder().message("exists").build())
        val repository = DynamoDbAccountRepository(client, "Accounts")

        repository.createIfAbsent(Account("acc-1", "owner-1", 1L, "ENABLED", Money(BigDecimal.ZERO, "BRL")))
    }

    @Test
    fun `should update the balance when the version matches`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.updateItem(any(UpdateItemRequest::class.java))).willReturn(UpdateItemResponse.builder().build())
        val repository = DynamoDbAccountRepository(client, "Accounts")

        val updated = repository.updateBalance("acc-1", 0, Money(BigDecimal(100), "BRL"))

        assertTrue(updated)
    }

    @Test
    fun `should fail to update the balance on a version mismatch`() {
        val client = mock(DynamoDbClient::class.java)
        given(client.updateItem(any(UpdateItemRequest::class.java)))
            .willThrow(ConditionalCheckFailedException.builder().message("mismatch").build())
        val repository = DynamoDbAccountRepository(client, "Accounts")

        val updated = repository.updateBalance("acc-1", 5, Money(BigDecimal(100), "BRL"))

        assertTrue(!updated)
    }

    private fun itemOf(
        id: String,
        owner: String = "owner-1",
        createdAt: Long = 1L,
        status: String = "ENABLED",
        balanceAmount: String = "0",
        balanceCurrency: String = "BRL",
        version: Long = 0,
    ): Map<String, AttributeValue> =
        mapOf(
            "accountId" to AttributeValue.builder().s(id).build(),
            "owner" to AttributeValue.builder().s(owner).build(),
            "createdAt" to AttributeValue.builder().n(createdAt.toString()).build(),
            "status" to AttributeValue.builder().s(status).build(),
            "balanceAmount" to AttributeValue.builder().n(balanceAmount).build(),
            "balanceCurrency" to AttributeValue.builder().s(balanceCurrency).build(),
            "version" to AttributeValue.builder().n(version.toString()).build(),
        )
}
