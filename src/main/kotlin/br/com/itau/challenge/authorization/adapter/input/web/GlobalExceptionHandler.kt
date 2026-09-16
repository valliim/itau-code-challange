package br.com.itau.challenge.authorization.adapter.input.web

import br.com.itau.challenge.authorization.adapter.input.web.response.ErrorResponse
import br.com.itau.challenge.authorization.domain.exception.ConcurrentBalanceUpdateException
import br.com.itau.challenge.authorization.domain.exception.InvalidTransactionException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import software.amazon.awssdk.core.exception.SdkException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = exception.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage.orEmpty()}" }
            .ifBlank { "Invalid request payload" }

        logger.warn("Validation failed for request: {}", message)
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val message = exception.constraintViolations
            .joinToString(", ") { "${it.propertyPath}: ${it.message}" }
            .ifBlank { "Invalid request payload" }

        logger.warn("Validation failed for request: {}", message)
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.warn("Malformed or unreadable request body", exception)
        return errorResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed or unreadable request body")
    }

    @ExceptionHandler(InvalidTransactionException::class)
    fun handleInvalidTransaction(exception: InvalidTransactionException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid transaction rule triggered: {}", exception.message)
        return errorResponse(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION", exception.message.orEmpty())
    }

    @ExceptionHandler(ConcurrentBalanceUpdateException::class)
    fun handleConcurrentBalanceUpdate(exception: ConcurrentBalanceUpdateException): ResponseEntity<ErrorResponse> {
        logger.warn("Concurrent balance update conflict: {}", exception.message)
        return errorResponse(HttpStatus.CONFLICT, "CONCURRENT_BALANCE_UPDATE", exception.message.orEmpty())
    }

    @ExceptionHandler(CallNotPermittedException::class, SdkException::class)
    fun handleInfrastructureExceptions(exception: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Persistence provider is unavailable: {}", exception.javaClass.simpleName, exception)
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Persistence provider is unavailable")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error while handling request", exception)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected internal error")
    }

    private fun errorResponse(
        status: HttpStatus,
        code: String,
        message: String,
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).body(ErrorResponse(code, message))
}