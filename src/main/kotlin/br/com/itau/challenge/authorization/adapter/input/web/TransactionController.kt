package br.com.itau.challenge.authorization.adapter.input.web

import br.com.itau.challenge.authorization.adapter.input.web.request.TransactionRequest
import br.com.itau.challenge.authorization.adapter.input.web.response.TransactionResponse
import br.com.itau.challenge.authorization.adapter.input.web.extension.toMoney
import br.com.itau.challenge.authorization.adapter.input.web.extension.toResponse
import br.com.itau.challenge.authorization.adapter.input.web.extension.toTransactionType
import br.com.itau.challenge.authorization.port.input.AuthorizeTransactionUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.validation.annotation.Validated

@RestController
@RequestMapping("/transactions")
@Validated
class TransactionController(
    private val authorizeTransactionUseCase: AuthorizeTransactionUseCase,
) {

    @PostMapping("/{transactionId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun authorize(
        @PathVariable
        @NotBlank(message = "transactionId must not be blank")
        @Size(max = 100, message = "transactionId must not exceed 100 characters")
        transactionId: String,
        @Valid @RequestBody request: TransactionRequest,
    ): TransactionResponse = authorizeTransactionUseCase.authorize(
        transactionId = transactionId,
        accountId = request.accountId,
        type = request.type.toTransactionType(),
        amount = request.amount.toMoney()
    ).toResponse()
}
