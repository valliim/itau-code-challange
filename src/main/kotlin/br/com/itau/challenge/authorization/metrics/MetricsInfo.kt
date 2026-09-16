package br.com.itau.challenge.authorization.metrics

object MetricsInfo {
    const val AUTHORIZATION_TRANSACTIONS = "authorization.transactions"
    const val ACCOUNT_EVENTS = "authorization.account.events"
    const val KAFKA_ACCOUNT_EVENTS = "authorization.kafka.account.events"
    const val AUTHORIZATION_DURATION = "authorization.duration"
    const val AUTHORIZATION_CONFLICTS = "authorization.balance.conflicts"

    const val RESULT_TAG = "result"
    const val REASON_TAG = "reason"

    const val RESULT_SUCCEEDED = "succeeded"
    const val RESULT_DECLINED = "declined"
    const val RESULT_REPLAY = "replay"
    const val RESULT_PROCESSED = "processed"
    const val RESULT_INVALID = "invalid"
    const val RESULT_FAILED = "failed"

    const val REASON_ACCOUNT_NOT_FOUND = "account_not_found"
    const val REASON_INSUFFICIENT_FUNDS = "insufficient_funds"
    const val ATTEMPT_TAG = "attempt"
}
