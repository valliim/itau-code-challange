package br.com.itau.challenge.authorization.domain.model

data class Account(
    val id: String,
    val owner: String,
    val createdAt: Long,
    val status: String,
    val balance: Money,
    val version: Long = 0,
)
