package br.com.itau.challenge.authorization.adapter.input.kafka.extension

import br.com.itau.challenge.authorization.adapter.input.kafka.dto.AccountCreatedMessage
import br.com.itau.challenge.authorization.domain.model.NewAccount

fun AccountCreatedMessage.toNewAccount(): NewAccount =
    NewAccount(
        id = account.id,
        owner = account.owner,
        createdAt = account.createdAt,
        status = account.status,
    )
