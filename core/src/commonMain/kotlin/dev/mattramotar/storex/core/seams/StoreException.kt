package dev.mattramotar.storex.core.seams

import kotlin.time.Duration

/**
 * Public exception model for seam-level fetch and coordination contracts.
 */
sealed class StoreException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    abstract val isRetryable: Boolean

    sealed class NetworkException(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {

        class Timeout(cause: Throwable? = null) :
            NetworkException("Network timeout", cause) {
            override val isRetryable: Boolean = true
        }

        class NoConnection(cause: Throwable? = null) :
            NetworkException("No network connection", cause) {
            override val isRetryable: Boolean = true
        }

        class HttpError(
            val statusCode: Int,
            val body: String? = null,
            cause: Throwable? = null
        ) : NetworkException("HTTP $statusCode", cause) {
            override val isRetryable: Boolean = when (statusCode) {
                408 -> true
                429 -> true
                in 500..599 -> true
                else -> false
            }
        }

        class DnsError(cause: Throwable? = null) :
            NetworkException("DNS resolution failed", cause) {
            override val isRetryable: Boolean = true
        }

        class SslError(cause: Throwable? = null) :
            NetworkException("SSL/TLS error", cause) {
            override val isRetryable: Boolean = false
        }
    }

    sealed class PersistenceException(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {

        class ReadError(cause: Throwable? = null) :
            PersistenceException("Failed to read from persistence", cause) {
            override val isRetryable: Boolean = true
        }

        class WriteError(cause: Throwable? = null) :
            PersistenceException("Failed to write to persistence", cause) {
            override val isRetryable: Boolean = true
        }

        class DeleteError(cause: Throwable? = null) :
            PersistenceException("Failed to delete from persistence", cause) {
            override val isRetryable: Boolean = true
        }

        class DiskFull(cause: Throwable? = null) :
            PersistenceException("Disk is full", cause) {
            override val isRetryable: Boolean = false
        }

        class PermissionDenied(cause: Throwable? = null) :
            PersistenceException("Permission denied", cause) {
            override val isRetryable: Boolean = false
        }

        class TransactionConflict(cause: Throwable? = null) :
            PersistenceException("Transaction conflict", cause) {
            override val isRetryable: Boolean = true
        }

        class DatabaseLocked(cause: Throwable? = null) :
            PersistenceException("Database is locked", cause) {
            override val isRetryable: Boolean = true
        }
    }

    class ValidationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false
    }

    class NotFound(
        key: String,
        cause: Throwable? = null
    ) : StoreException("Key not found: $key", cause) {
        override val isRetryable: Boolean = false
    }

    class SerializationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false
    }

    class ConfigurationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false
    }

    class RateLimited(
        val retryAfter: Duration? = null,
        cause: Throwable? = null
    ) : StoreException(rateLimitedMessage(retryAfter), cause) {
        override val isRetryable: Boolean = true
    }

    class Unknown(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = true
    }

    companion object {
        private fun String.containsText(fragment: String): Boolean =
            contains(fragment, ignoreCase = true)

        private fun rateLimitedMessage(retryAfter: Duration?): String =
            if (retryAfter != null) "Rate limited (retry after $retryAfter)" else "Rate limited"

        fun from(throwable: Throwable): StoreException {
            if (throwable is kotlinx.coroutines.CancellationException) {
                throw throwable
            }

            return when (throwable) {
                is StoreException -> throwable
                is kotlinx.serialization.SerializationException ->
                    SerializationError("Serialization failed: ${throwable.message}", throwable)

                else -> {
                    val message = throwable.message ?: return Unknown("Unknown error", throwable)
                    when {
                        message.containsText("timeout") ->
                            NetworkException.Timeout(throwable)

                        message.containsText("connection") ->
                            NetworkException.NoConnection(throwable)

                        message.containsText("not found") ->
                            NotFound(message, throwable)

                        message.containsText("permission") || message.containsText("access denied") ->
                            PersistenceException.PermissionDenied(throwable)

                        message.containsText("disk full") || message.containsText("no space") ->
                            PersistenceException.DiskFull(throwable)

                        message.containsText("rate limit") || message.containsText("too many requests") ->
                            RateLimited(retryAfter = null, cause = throwable)

                        message.containsText("validation") || message.containsText("invalid") ->
                            ValidationError(message, throwable)

                        message.containsText("serialization") ||
                            message.containsText("deserialization") ||
                            message.containsText("parse") ->
                            SerializationError(message, throwable)

                        message.containsText("configuration") || message.containsText("misconfigured") ->
                            ConfigurationError(message, throwable)

                        message.containsText("lock") ->
                            PersistenceException.DatabaseLocked(throwable)

                        message.containsText("conflict") ->
                            PersistenceException.TransactionConflict(throwable)

                        message.containsText("dns") ->
                            NetworkException.DnsError(throwable)

                        message.containsText("ssl") ||
                            message.containsText("tls") ||
                            message.containsText("certificate") ->
                            NetworkException.SslError(throwable)

                        else -> Unknown(message, throwable)
                    }
                }
            }
        }

        fun fromHttpStatus(
            statusCode: Int,
            body: String? = null,
            cause: Throwable? = null
        ): NetworkException.HttpError {
            return NetworkException.HttpError(statusCode, body, cause)
        }

        fun rateLimited(
            retryAfter: Duration? = null,
            cause: Throwable? = null
        ): RateLimited {
            return RateLimited(retryAfter, cause)
        }
    }
}
