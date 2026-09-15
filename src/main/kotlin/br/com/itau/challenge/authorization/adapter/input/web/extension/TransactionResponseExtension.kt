package br.com.itau.challenge.authorization.adapter.input.web.extension

import br.com.itau.challenge.authorization.adapter.input.web.response.AccountInfo
import br.com.itau.challenge.authorization.adapter.input.web.response.AmountInfo
import br.com.itau.challenge.authorization.adapter.input.web.response.BalanceInfo
import br.com.itau.challenge.authorization.adapter.input.web.response.TransactionInfo
import br.com.itau.challenge.authorization.adapter.input.web.response.TransactionResponse
import br.com.itau.challenge.authorization.domain.model.AuthorizationResult

fun AuthorizationResult.toResponse(): TransactionResponse =
    TransactionResponse(
        transaction = TransactionInfo(
            id = transaction.id,
            type = transaction.type.name,
            amount = AmountInfo(
                value = transaction.amount.amount,
                currency = transaction.amount.currency
            ),
            status = transaction.status.name,
            timestamp = transaction.timestamp,
        ),
        account = AccountInfo(
            id = accountId,
            balance = BalanceInfo(
                amount = balance.amount,
                currency = balance.currency
            )
        ),
    )
