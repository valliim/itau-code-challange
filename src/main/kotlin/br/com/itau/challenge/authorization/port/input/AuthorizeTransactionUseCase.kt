package br.com.itau.challenge.authorization.port.input

import br.com.itau.challenge.authorization.domain.model.AuthorizationResult
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.TransactionType

fun interface AuthorizeTransactionUseCase {
    fun authorize(
        transactionId: String,
        accountId: String,
        type: TransactionType,
        amount: Money,
    ): AuthorizationResult
}
