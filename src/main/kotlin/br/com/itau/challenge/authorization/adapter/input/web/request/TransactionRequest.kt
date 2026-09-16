package br.com.itau.challenge.authorization.adapter.input.web.request

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal
import org.hibernate.validator.constraints.UUID
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TransactionRequest(

    @field:NotNull(message = "account_id must not be null")
    @field:UUID(message = "account_id must be a valid UUID")
    val accountId: String,

    @field:NotNull(message = "type must not be null")
    @field:NotBlank(message = "type must not be blank")
    val type: String,

    @field:NotNull(message = "amount must not be null")
    @field:Valid
    val amount: AmountRequest,
)

data class AmountRequest(
    @field:NotNull(message = "amount.value must not be null")
    @field:DecimalMin(value = "0.0", inclusive = false, message = "amount.value must be greater than zero")
    val value: BigDecimal,

    @field:NotBlank(message = "amount.currency must not be blank")
    @field:Pattern(regexp = "BRL", message = "amount.currency must be BRL")
    val currency: String,
)