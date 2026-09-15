package br.com.itau.challenge.authorization.port.output

import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.Money


interface AccountRepository {
    fun findById(accountId: String): Account?
    fun createIfAbsent(account: Account)
    fun updateBalance(
        accountId: String,
        expectedVersion: Long,
        newBalance: Money,
    ): Boolean
}
