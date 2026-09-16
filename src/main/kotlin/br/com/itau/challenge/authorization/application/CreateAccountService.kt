package br.com.itau.challenge.authorization.application

import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.NewAccount
import br.com.itau.challenge.authorization.port.input.CreateAccountUseCase
import br.com.itau.challenge.authorization.port.output.AccountRepository
import br.com.itau.challenge.authorization.metrics.MetricsInfo
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import org.springframework.stereotype.Service
import java.math.BigDecimal

private const val DEFAULT_CURRENCY = "BRL"

@Service
class CreateAccountService(
    private val accountRepository: AccountRepository,
    private val meterRegistry: MeterRegistry = Metrics.globalRegistry,
) : CreateAccountUseCase {

    override fun createAccount(newAccount: NewAccount) {
        val account =
            Account(
                id = newAccount.id,
                owner = newAccount.owner,
                createdAt = newAccount.createdAt,
                status = newAccount.status,
                balance = Money(BigDecimal.ZERO, DEFAULT_CURRENCY),
                version = 0,
            )
        accountRepository.createIfAbsent(account)
            meterRegistry.counter(
                MetricsInfo.ACCOUNT_EVENTS,
                MetricsInfo.RESULT_TAG,
                MetricsInfo.RESULT_PROCESSED,
            ).increment()
    }
}
