package br.com.itau.challenge.authorization.adapter.input.web.response

import java.time.OffsetDateTime

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
