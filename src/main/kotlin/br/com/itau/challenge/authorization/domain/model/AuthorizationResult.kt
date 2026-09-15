package br.com.itau.challenge.authorization.domain.model

data class AuthorizationResult(
    val transaction: Transaction,
    val accountId: String,
    val balance: Money
)
