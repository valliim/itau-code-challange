package br.com.itau.challenge.authorization.adapter.input.kafka.dto

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class AccountCreatedMessage(val account: AccountPayload)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AccountPayload(
    val id: String,
    val owner: String,
    val createdAt: Long,
    val status: String,
)
