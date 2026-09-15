package br.com.itau.challenge.authorization.domain.exception

class AccountNotFoundException(accountId: String) : RuntimeException("Account not found: $accountId")
