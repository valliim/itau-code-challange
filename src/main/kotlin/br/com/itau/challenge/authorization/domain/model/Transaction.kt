package br.com.itau.challenge.authorization.domain.model

import java.time.OffsetDateTime

data class Transaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Money,
    val status: TransactionStatus,
    val timestamp: OffsetDateTime,
)
