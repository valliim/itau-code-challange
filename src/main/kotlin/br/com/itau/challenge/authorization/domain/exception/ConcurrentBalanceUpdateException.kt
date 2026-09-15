package br.com.itau.challenge.authorization.domain.exception

class ConcurrentBalanceUpdateException(accountId: String) :
    RuntimeException("Could not update balance for account '$accountId' due to concurrent modifications")
