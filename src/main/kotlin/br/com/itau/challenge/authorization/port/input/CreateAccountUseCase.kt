package br.com.itau.challenge.authorization.port.input

import br.com.itau.challenge.authorization.domain.model.NewAccount

fun interface CreateAccountUseCase {
    fun createAccount(newAccount: NewAccount)
}
