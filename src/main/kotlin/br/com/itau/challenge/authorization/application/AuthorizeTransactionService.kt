package br.com.itau.challenge.authorization.application

import br.com.itau.challenge.authorization.domain.exception.AccountNotFoundException
import br.com.itau.challenge.authorization.domain.exception.ConcurrentBalanceUpdateException
import br.com.itau.challenge.authorization.domain.exception.InvalidTransactionException
import br.com.itau.challenge.authorization.domain.model.Account
import br.com.itau.challenge.authorization.domain.model.AuthorizationResult
import br.com.itau.challenge.authorization.domain.model.Money
import br.com.itau.challenge.authorization.domain.model.Transaction
import br.com.itau.challenge.authorization.domain.model.TransactionStatus
import br.com.itau.challenge.authorization.domain.model.TransactionType
import br.com.itau.challenge.authorization.port.input.AuthorizeTransactionUseCase
import br.com.itau.challenge.authorization.port.output.AccountRepository
import br.com.itau.challenge.authorization.port.output.TransactionRepository
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.OffsetDateTime

private const val MAX_BALANCE_UPDATE_ATTEMPTS = 5
private const val TRANSACTION_ID_MDC_KEY = "transactionId"
private const val ACCOUNT_ID_MDC_KEY = "accountId"

@Service
class AuthorizeTransactionService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AuthorizeTransactionUseCase {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun authorize(
        transactionId: String,
        accountId: String,
        type: TransactionType,
        amount: Money,
    ): AuthorizationResult {
        MDC.put(TRANSACTION_ID_MDC_KEY, transactionId)
        MDC.put(ACCOUNT_ID_MDC_KEY, accountId)

        try {
            return authorizeTransaction(
                transactionId = transactionId,
                accountId = accountId,
                type = type,
                amount = amount
            )
        } finally {
            MDC.remove(TRANSACTION_ID_MDC_KEY)
            MDC.remove(ACCOUNT_ID_MDC_KEY)
        }
    }

    private fun authorizeTransaction(
        transactionId: String,
        accountId: String,
        type: TransactionType,
        amount: Money,
    ): AuthorizationResult {
        logger.info("Starting authorization of {} transaction of {} {}", type, amount.amount, amount.currency)
        validateAmount(amount)
        replayIfExists(transactionId)?.let { return it }

        val timestamp = OffsetDateTime.now(clock)

        val account = findAccount(accountId)
            ?: return declineForMissingAccount(
                transactionId = transactionId,
                accountId = accountId,
                type = type,
                amount = amount,
                timestamp = timestamp
            )

        return attemptAuthorization(
            transactionId = transactionId,
            type = type,
            amount = amount,
            account = account,
            timestamp = timestamp
        )
    }

    private fun validateAmount(amount: Money) {
        if (amount.amount <= BigDecimal.ZERO) {
            logger.warn("Authorization rejected: transaction amount must be greater than zero")
            throw InvalidTransactionException("Transaction amount must be greater than zero")
        }
    }

    private fun replayIfExists(transactionId: String): AuthorizationResult? =
        transactionRepository.findById(transactionId)?.let { existingTransaction ->
            logger.info("Replaying already processed transaction with status {}", existingTransaction.status)

            when (val account = accountRepository.findById(existingTransaction.accountId)) {
                null -> {
                    logger.warn("Account not found while replaying transaction: returning declined result")
                    declined(transaction = existingTransaction, accountId = existingTransaction.accountId)
                }

                else -> {
                    AuthorizationResult(transaction = existingTransaction, accountId = account.id, balance = account.balance)
                }
            }
        }

    private fun findAccount(accountId: String): Account? = accountRepository.findById(accountId)

    private fun declineForMissingAccount(
        transactionId: String,
        accountId: String,
        type: TransactionType,
        amount: Money,
        timestamp: OffsetDateTime,
    ): AuthorizationResult {
        logger.warn("Authorization declined: account does not exist")
        return declined(
            Transaction(
                id = transactionId,
                accountId = accountId,
                type = type,
                amount = amount,
                status = TransactionStatus.FAILED,
                timestamp = timestamp
            ),
            accountId = accountId,
        )
    }

    private fun declined(
        transaction: Transaction,
        accountId: String,
    ): AuthorizationResult = AuthorizationResult(
        transaction = transaction,
        accountId = accountId,
        balance = Money(BigDecimal.ZERO, transaction.amount.currency),
    )

    private fun attemptAuthorization(
        transactionId: String,
        type: TransactionType,
        amount: Money,
        account: Account,
        timestamp: OffsetDateTime,
    ): AuthorizationResult {
        val newBalanceAmount = applyTransaction(type = type, balance = account.balance.amount, amount = amount.amount)

        if (isInsufficientFunds(type, newBalanceAmount)) {
            return declineForInsufficientFunds(
                transactionId = transactionId,
                type = type,
                amount = amount,
                account = account,
                timestamp = timestamp
            )
        }
        val newBalance = Money(amount = newBalanceAmount, currency = account.balance.currency)

        return updateBalanceWithRetry(
            account = account,
            newBalance = newBalance,
            transactionId = transactionId,
            type = type,
            amount = amount,
            timestamp = timestamp
        )
    }

    private fun declineForInsufficientFunds(
        transactionId: String,
        type: TransactionType,
        amount: Money,
        account: Account,
        timestamp: OffsetDateTime,
    ): AuthorizationResult {
        logger.warn(
            "Authorization declined: insufficient funds, balance is {} and requested amount is {}",
            account.balance.amount,
            amount.amount,
        )
        val failedTransaction =
            Transaction(
                id = transactionId,
                accountId = account.id,
                type = type,
                amount = amount,
                status = TransactionStatus.FAILED,
                timestamp = timestamp
            )

        transactionRepository.save(failedTransaction)
        return AuthorizationResult(transaction = failedTransaction, accountId = account.id, balance = account.balance)
    }

    private tailrec fun updateBalanceWithRetry(
        account: Account,
        newBalance: Money,
        transactionId: String,
        type: TransactionType,
        amount: Money,
        timestamp: OffsetDateTime,
        attempt: Int = 1,
    ): AuthorizationResult {
        val updated = accountRepository.updateBalance(accountId = account.id, expectedVersion = account.version, newBalance = newBalance)

        if (!updated) {
            if (attempt >= MAX_BALANCE_UPDATE_ATTEMPTS) {
                logger.error(
                    "Authorization failed: balance update exhausted all {} attempts due to concurrent updates",
                    MAX_BALANCE_UPDATE_ATTEMPTS,
                )
                throw ConcurrentBalanceUpdateException(account.id)
            }
            logger.warn(
                "Optimistic lock conflict on balance update (attempt {} of {}): reloading account and retrying",
                attempt,
                MAX_BALANCE_UPDATE_ATTEMPTS,
            )
            val reloadedAccount =
                accountRepository.findById(account.id) ?: run {
                    logger.warn("Account disappeared while retrying balance update")
                    throw AccountNotFoundException(account.id)
                }
            val newBalanceAmount = applyTransaction(type = type, balance = reloadedAccount.balance.amount, amount = amount.amount)

            if (isInsufficientFunds(type, newBalanceAmount)) {
                return declineForInsufficientFunds(
                    transactionId = transactionId,
                    type = type,
                    amount = amount,
                    account = reloadedAccount,
                    timestamp = timestamp
                )
            }

            val recalculatedBalance = Money(newBalanceAmount, reloadedAccount.balance.currency)
            return updateBalanceWithRetry(
                account = reloadedAccount,
                newBalance = recalculatedBalance,
                transactionId = transactionId,
                type = type,
                amount = amount,
                timestamp = timestamp,
                attempt = attempt + 1,
            )
        }

        val succeededTransaction = Transaction(
            id = transactionId,
            accountId = account.id,
            type = type,
            amount = amount,
            status = TransactionStatus.SUCCEEDED,
            timestamp = timestamp
        )
        transactionRepository.save(succeededTransaction)
        logger.info("Authorization approved: new balance is {} {}", newBalance.amount, newBalance.currency)
        return AuthorizationResult(transaction = succeededTransaction, accountId = account.id, balance = newBalance)
    }

    private fun isInsufficientFunds(type: TransactionType, newBalanceAmount: BigDecimal): Boolean =
        type == TransactionType.DEBIT && newBalanceAmount < BigDecimal.ZERO

    private fun applyTransaction(type: TransactionType, balance: BigDecimal, amount: BigDecimal): BigDecimal =
        when (type) {
            TransactionType.CREDIT -> balance + amount
            TransactionType.DEBIT -> balance - amount
        }
}
