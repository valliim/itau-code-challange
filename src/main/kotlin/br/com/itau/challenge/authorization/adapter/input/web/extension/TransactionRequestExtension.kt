package br.com.itau.challenge.authorization.adapter.input.web.extension

import br.com.itau.challenge.authorization.adapter.input.web.request.AmountRequest
import br.com.itau.challenge.authorization.domain.exception.InvalidTransactionException
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.TransactionType

fun AmountRequest.toMoney(): Money = Money(value, currency)

fun String.toTransactionType(): TransactionType =
    runCatching { TransactionType.valueOf(this.uppercase()) }
        .getOrElse { throw InvalidTransactionException("Invalid transaction type: $this") }