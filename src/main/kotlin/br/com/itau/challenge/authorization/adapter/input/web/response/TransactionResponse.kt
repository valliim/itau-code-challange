package br.com.itau.challenge.authorization.adapter.input.web.response

import java.math.BigDecimal
import java.time.OffsetDateTime

data class TransactionResponse(
    val transaction: TransactionInfo, val account: AccountInfo
)

data class TransactionInfo(
    val id: String,
    val type: String,
    val amount: AmountInfo,
    val status: String,
    val timestamp: OffsetDateTime,
)

data class AmountInfo(
    val value: BigDecimal, val currency: String
)

data class AccountInfo(
    val id: String, val balance: BalanceInfo
)

data class BalanceInfo(
    val amount: BigDecimal, val currency: String
)
