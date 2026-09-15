package br.com.itau.challenge.authorization.port.output

import br.com.itau.challenge.authorization.domain.model.Transaction

interface TransactionRepository {
    fun findById(transactionId: String): Transaction?
    fun save(transaction: Transaction)
}
