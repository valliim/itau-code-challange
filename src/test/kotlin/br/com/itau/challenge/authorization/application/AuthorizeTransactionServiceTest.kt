package br.com.itau.challenge.authorization.application

import br.com.itau.challenge.authorization.domain.exception.AccountNotFoundException
import br.com.itau.challenge.authorization.domain.exception.ConcurrentBalanceUpdateException
import br.com.itau.challenge.authorization.domain.exception.InvalidTransactionException
import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.Transaction
import br.com.itau.challenge.authorization.domain.model.TransactionStatus
import br.com.itau.challenge.authorization.domain.model.TransactionType
import br.com.itau.challenge.authorization.port.output.AccountRepository
import br.com.itau.challenge.authorization.port.output.TransactionRepository
import org.slf4j.MDC
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthorizeTransactionServiceTest {

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2025-07-08T18:57:55Z"), ZoneOffset.UTC)
    private val defaultAccountId = "acc-1"
    private val defaultTransactionId = "txn-123"

    @Test
    fun `should approve a credit and increase the account balance`() {
        val accountRepository = InMemoryAccountRepository(listOf(accountOf(balance = BigDecimal(100))))
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        val result = service.authorize(defaultTransactionId, defaultAccountId, TransactionType.CREDIT, Money(BigDecimal(50), "BRL"))

        assertEquals(TransactionStatus.SUCCEEDED, result.transaction.status)
        assertEquals(BigDecimal(150), result.balance.amount)
    }

    @Test
    fun `should approve a debit that exactly zeroes the balance`() {
        val accountRepository = InMemoryAccountRepository(listOf(accountOf(balance = BigDecimal(50))))
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        val result = service.authorize(defaultTransactionId, defaultAccountId, TransactionType.DEBIT, Money(BigDecimal(50), "BRL"))

        assertEquals(TransactionStatus.SUCCEEDED, result.transaction.status)
        assertEquals(BigDecimal.ZERO, result.balance.amount)
    }

    @Test
    fun `should refuse a debit that would leave the balance negative without mutating it`() {
        val accountRepository = InMemoryAccountRepository(listOf(accountOf(balance = BigDecimal(30))))
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        val result = service.authorize(defaultTransactionId, defaultAccountId, TransactionType.DEBIT, Money(BigDecimal(50), "BRL"))

        assertEquals(TransactionStatus.FAILED, result.transaction.status)
        assertEquals(BigDecimal(30), result.balance.amount)
        assertEquals(BigDecimal(30), accountRepository.findById(defaultAccountId)?.balance?.amount)
    }

    @Test
    fun `should refuse a transaction for a non-existent account`() {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        val result = service.authorize(defaultTransactionId, "missing-acc", TransactionType.CREDIT, Money(BigDecimal(10), "BRL"))

        assertEquals(TransactionStatus.FAILED, result.transaction.status)
        assertEquals("missing-acc", result.accountId)
        assertEquals(BigDecimal.ZERO, result.balance.amount)
        assertTrue(transactionRepository.transactions.isEmpty())
    }

    @Test
    fun `should refuse a replayed transaction whose account no longer exists`() {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        transactionRepository.save(
            Transaction(
                defaultTransactionId,
                "missing-acc",
                TransactionType.CREDIT,
                Money(BigDecimal(10), "BRL"),
                TransactionStatus.SUCCEEDED,
                OffsetDateTime.now(fixedClock),
            )
        )

        val result = service.authorize(defaultTransactionId, "missing-acc", TransactionType.CREDIT, Money(BigDecimal(10), "BRL"))

        assertEquals("missing-acc", result.accountId)
        assertEquals(BigDecimal.ZERO, result.balance.amount)
    }

    @Test
    fun `should reject a transaction with a non-positive amount`() {
        val accountRepository = InMemoryAccountRepository(listOf(accountOf()))
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        assertFailsWith<InvalidTransactionException> {
            service.authorize(defaultTransactionId, defaultAccountId, TransactionType.CREDIT, Money(BigDecimal.ZERO, "BRL"))
        }

        @Test
        fun `should propagate transaction persistence failure after balance update`() {
            val accountRepository = InMemoryAccountRepository(listOf(accountOf()))
            val transactionRepository = object : TransactionRepository {
                override fun findById(transactionId: String): Transaction? = null
                override fun save(transaction: Transaction): Unit = error("transaction store unavailable")
            }
            val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

            assertFailsWith<IllegalStateException> {
                service.authorize(
                    defaultTransactionId,
                    defaultAccountId,
                    TransactionType.CREDIT,
                    Money(BigDecimal(10), "BRL"),
                )
            }
            assertEquals(BigDecimal(10), accountRepository.findById(defaultAccountId)?.balance?.amount)
        }

        @Test
        fun `should reject a transaction in a currency other than BRL`() {
            val accountRepository = InMemoryAccountRepository(listOf(accountOf()))
            val transactionRepository = InMemoryTransactionRepository()
            val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

            val exception = assertFailsWith<InvalidTransactionException> {
                service.authorize(
                    defaultTransactionId,
                    defaultAccountId,
                    TransactionType.CREDIT,
                    Money(BigDecimal(10), "USD"),
                )
            }

            assertEquals("Only BRL transactions are supported", exception.message)
            assertEquals(BigDecimal.ZERO, accountRepository.findById(defaultAccountId)?.balance?.amount)
        }

        @Test
        fun `should reject a transaction for an account that is not enabled`() {
            val accountRepository = InMemoryAccountRepository(listOf(accountOf(status = "BLOCKED")))
            val transactionRepository = InMemoryTransactionRepository()
            val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

            val exception = assertFailsWith<InvalidTransactionException> {
                service.authorize(
                    defaultTransactionId,
                    defaultAccountId,
                    TransactionType.CREDIT,
                    Money(BigDecimal(10), "BRL"),
                )
            }

            assertEquals("Account $defaultAccountId is not enabled", exception.message)
            assertEquals(BigDecimal.ZERO, accountRepository.findById(defaultAccountId)?.balance?.amount)
        }
    }

    @Test
    fun `should return the already persisted result when the same transaction id is replayed`() {
        val accountRepository = InMemoryAccountRepository(listOf(accountOf(balance = BigDecimal(100))))
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        val firstResult = service.authorize(defaultTransactionId, defaultAccountId, TransactionType.CREDIT, Money(BigDecimal(50), "BRL"))

        val secondResult = service.authorize(defaultTransactionId, defaultAccountId, TransactionType.DEBIT, Money(BigDecimal(999), "BRL"))

        assertEquals(firstResult.transaction, secondResult.transaction)
        assertEquals(BigDecimal(150), secondResult.balance.amount)
    }

    @Test
    fun `should retry on a concurrent version conflict and eventually succeed`() {
        val account = accountOf(balance = BigDecimal(100))
        val accountRepository = object : AccountRepository {
            var accounts = mutableMapOf(account.id to account)
            var calls = 0

            override fun findById(accountId: String): Account? = accounts[accountId]
            override fun createIfAbsent(account: Account) { accounts.putIfAbsent(account.id, account) }

            override fun updateBalance(accountId: String, expectedVersion: Long, newBalance: Money): Boolean {
                calls++
                if (calls == 1) return false // simulate a concurrent write winning the first race

                val current = accounts[accountId] ?: return false
                accounts[accountId] = current.copy(balance = newBalance, version = current.version + 1)
                return true
            }
        }

        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        val result = service.authorize(defaultTransactionId, defaultAccountId, TransactionType.CREDIT, Money(BigDecimal(50), "BRL"))

        assertEquals(TransactionStatus.SUCCEEDED, result.transaction.status)
        assertTrue(accountRepository.calls > 1)
    }

    @Test
    fun `should decline a debit for insufficient funds when a concurrent update lowers the balance on retry`() {
        val account = accountOf(balance = BigDecimal(100))
        val accountRepository = object : AccountRepository {
            var accounts = mutableMapOf(account.id to account)
            var calls = 0

            override fun findById(accountId: String): Account? = accounts[accountId]
            override fun createIfAbsent(account: Account) { accounts.putIfAbsent(account.id, account) }

            override fun updateBalance(accountId: String, expectedVersion: Long, newBalance: Money): Boolean {
                calls++
                if (calls == 1) {
                    accounts[accountId] = accounts[accountId]!!.copy(balance = Money(BigDecimal(30), "BRL"), version = 1)
                    return false
                }
                val current = accounts[accountId] ?: return false
                accounts[accountId] = current.copy(balance = newBalance, version = current.version + 1)
                return true
            }
        }

        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        val result = service.authorize(defaultTransactionId, defaultAccountId, TransactionType.DEBIT, Money(BigDecimal(50), "BRL"))

        assertEquals(TransactionStatus.FAILED, result.transaction.status)
        assertEquals(BigDecimal(30), result.balance.amount)
        assertEquals(BigDecimal(30), accountRepository.accounts[defaultAccountId]?.balance?.amount)
    }

    @Test
    fun `should give up after exhausting retries on persistent concurrent conflicts`() {
        val account = accountOf(balance = BigDecimal(100))
        val accountRepository = object : AccountRepository {
            override fun findById(accountId: String): Account? = account
            override fun createIfAbsent(account: Account) = Unit
            override fun updateBalance(accountId: String, expectedVersion: Long, newBalance: Money): Boolean = false
        }
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        assertFailsWith<ConcurrentBalanceUpdateException> {
            service.authorize(defaultTransactionId, defaultAccountId, TransactionType.CREDIT, Money(BigDecimal(50), "BRL"))
        }
    }

    @Test
    fun `should fail with account not found when the account disappears while retrying a balance update`() {
        val account = accountOf(balance = BigDecimal(100))
        val accountRepository = object : AccountRepository {
            var accounts = mutableMapOf(account.id to account)
            var calls = 0

            override fun findById(accountId: String): Account? =
                if (calls == 0) accounts[accountId] else null

            override fun createIfAbsent(account: Account) { accounts.putIfAbsent(account.id, account) }

            override fun updateBalance(accountId: String, expectedVersion: Long, newBalance: Money): Boolean {
                calls++
                return false
            }
        }

        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        assertFailsWith<AccountNotFoundException> {
            service.authorize(defaultTransactionId, defaultAccountId, TransactionType.CREDIT, Money(BigDecimal(50), "BRL"))
        }
    }

    @Test
    fun `should not leak correlation values in the mdc after a successful authorization`() {
        val accountRepository = InMemoryAccountRepository(listOf(accountOf(balance = BigDecimal(100))))
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        service.authorize(defaultTransactionId, defaultAccountId, TransactionType.CREDIT, Money(BigDecimal(50), "BRL"))

        assertNull(MDC.get("transactionId"))
        assertNull(MDC.get("accountId"))
    }

    @Test
    fun `should not leak correlation values in the mdc when the authorization fails`() {
        val accountRepository = InMemoryAccountRepository(listOf(accountOf()))
        val transactionRepository = InMemoryTransactionRepository()
        val service = AuthorizeTransactionService(accountRepository, transactionRepository, fixedClock)

        assertFailsWith<InvalidTransactionException> {
            service.authorize(defaultTransactionId, defaultAccountId, TransactionType.CREDIT, Money(BigDecimal.ZERO, "BRL"))
        }

        assertNull(MDC.get("transactionId"))
        assertNull(MDC.get("accountId"))
    }

    private class InMemoryAccountRepository(initial: List<Account> = emptyList()) : AccountRepository {
        val accounts = mutableMapOf<String, Account>().apply { initial.forEach { put(it.id, it) } }
        var updateAttempts = 0

        override fun findById(accountId: String): Account? = accounts[accountId]
        override fun createIfAbsent(account: Account) { accounts.putIfAbsent(account.id, account) }

        override fun updateBalance(accountId: String, expectedVersion: Long, newBalance: Money): Boolean {
            updateAttempts++
            val account = accounts[accountId] ?: return false
            if (account.version != expectedVersion) return false
            accounts[accountId] = account.copy(balance = newBalance, version = account.version + 1)
            return true
        }
    }

    private class InMemoryTransactionRepository : TransactionRepository {
        val transactions = mutableMapOf<String, Transaction>()

        override fun findById(transactionId: String): Transaction? = transactions[transactionId]
        override fun save(transaction: Transaction) { transactions[transaction.id] = transaction }
    }

    private fun accountOf(
        id: String = defaultAccountId,
        balance: BigDecimal = BigDecimal.ZERO,
        version: Long = 0,
        status: String = "ENABLED",
    ): Account = Account(
        id = id,
        owner = "owner-1",
        createdAt = 1L,
        status = status,
        balance = Money(balance, "BRL"),
        version = version
    )
}