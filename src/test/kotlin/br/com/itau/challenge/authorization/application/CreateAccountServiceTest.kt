package br.com.itau.challenge.authorization.application

import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.NewAccount
import br.com.itau.challenge.authorization.port.output.AccountRepository
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateAccountServiceTest {

    @Test
    fun `should create an account with a zero balance`() {
        val repository = FakeAccountRepository()
        val service = CreateAccountService(repository)

        service.createAccount(NewAccount(id = "acc-1", owner = "owner-1", createdAt = 1L, status = "ENABLED"))

        val account = repository.findById("acc-1")
        assertEquals(BigDecimal.ZERO, account?.balance?.amount)
        assertEquals("BRL", account?.balance?.currency)
    }

    @Test
    fun `should not duplicate the balance when the same account id is created twice`() {
        val repository = FakeAccountRepository()
        val service = CreateAccountService(repository)
        val newAccount = NewAccount(id = "acc-1", owner = "owner-1", createdAt = 1L, status = "ENABLED")

        service.createAccount(newAccount)
        repository.updateBalance("acc-1", 0, Money(BigDecimal(100), "BRL"))
        service.createAccount(newAccount)

        assertEquals(BigDecimal(100), repository.findById("acc-1")?.balance?.amount)
    }

    private class FakeAccountRepository : AccountRepository {
        val accounts = mutableMapOf<String, Account>()

        override fun findById(accountId: String): Account? = accounts[accountId]

        override fun createIfAbsent(account: Account) {
            accounts.putIfAbsent(account.id, account)
        }

        override fun updateBalance(
            accountId: String,
            expectedVersion: Long,
            newBalance: Money,
        ): Boolean {
            val account = accounts[accountId] ?: return false
            if (account.version != expectedVersion) return false
            accounts[accountId] = account.copy(balance = newBalance, version = account.version + 1)
            return true
        }
    }
}
