package br.com.itau.challenge.authorization.adapter.input.kafka

class InvalidAccountEventException(
    cause: Throwable,
) : RuntimeException("Invalid account-created event payload", cause)
