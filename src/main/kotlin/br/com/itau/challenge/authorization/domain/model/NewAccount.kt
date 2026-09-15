package br.com.itau.challenge.authorization.domain.model

data class NewAccount(
    val id: String,
    val owner: String,
    val createdAt: Long,
    val status: String,
)
