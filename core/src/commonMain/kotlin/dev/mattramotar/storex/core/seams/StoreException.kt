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
    ) : StoreException("Rate limited${retryAfter?.let { " (retry after $it)" } ?: ""}", cause) {
        override val isRetryable: Boolean = true
    }

    class Unknown(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = true
    }

    companion object {
        fun from(throwable: Throwable): StoreException {
            if (throwable is kotlinx.coroutines.CancellationException) {
                throw throwable
            }

            return when (throwable) {
                is StoreException -> throwable
                is kotlinx.serialization.SerializationException ->
                    SerializationError("Serialization failed: ${throwable.message}", throwable)

                else -> when {
                    throwable.message?.contains("timeout", ignoreCase = true) == true ->
                        NetworkException.Timeout(throwable)

                    throwable.message?.contains("connection", ignoreCase = true) == true ->
                        NetworkException.NoConnection(throwable)

                    throwable.message?.contains("not found", ignoreCase = true) == true ->
                        NotFound(throwable.message ?: "Unknown", throwable)

                    throwable.message?.contains("permission", ignoreCase = true) == true ||
                        throwable.message?.contains("access denied", ignoreCase = true) == true ->
                        PersistenceException.PermissionDenied(throwable)

                    throwable.message?.contains("disk full", ignoreCase = true) == true ||
                        throwable.message?.contains("no space", ignoreCase = true) == true ->
                        PersistenceException.DiskFull(throwable)

                    throwable.message?.contains("rate limit", ignoreCase = true) == true ||
                        throwable.message?.contains("too many requests", ignoreCase = true) == true ->
                        RateLimited(retryAfter = null, cause = throwable)

                    throwable.message?.contains("validation", ignoreCase = true) == true ||
                        throwable.message?.contains("invalid", ignoreCase = true) == true ->
                        ValidationError(throwable.message ?: "Validation failed", throwable)

                    throwable.message?.contains("serialization", ignoreCase = true) == true ||
                        throwable.message?.contains("deserialization", ignoreCase = true) == true ||
                        throwable.message?.contains("parse", ignoreCase = true) == true ->
                        SerializationError(throwable.message ?: "Serialization error", throwable)

                    throwable.message?.contains("configuration", ignoreCase = true) == true ||
                        throwable.message?.contains("misconfigured", ignoreCase = true) == true ->
                        ConfigurationError(throwable.message ?: "Configuration error", throwable)

                    throwable.message?.contains("lock", ignoreCase = true) == true ->
                        PersistenceException.DatabaseLocked(throwable)

                    throwable.message?.contains("conflict", ignoreCase = true) == true ->
                        PersistenceException.TransactionConflict(throwable)

                    throwable.message?.contains("dns", ignoreCase = true) == true ->
                        NetworkException.DnsError(throwable)

                    throwable.message?.contains("ssl", ignoreCase = true) == true ||
                        throwable.message?.contains("tls", ignoreCase = true) == true ||
                        throwable.message?.contains("certificate", ignoreCase = true) == true ->
                        NetworkException.SslError(throwable)

                    else -> Unknown(throwable.message ?: "Unknown error", throwable)
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
