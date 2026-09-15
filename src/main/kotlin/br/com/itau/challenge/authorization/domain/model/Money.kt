package br.com.itau.challenge.authorization.domain.model

import java.math.BigDecimal

data class Money(
    val amount: BigDecimal,
    val currency: String
)
