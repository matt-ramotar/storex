package dev.mattramotar.storex.core.internal

import kotlin.time.Duration

/**
 * Base class for all Store exceptions.
 *
 * All exceptions include an [isRetryable] property to indicate whether the operation
 * should be retried. This is used by retry policies and circuit breakers.
 */
sealed class StoreException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Indicates whether this exception represents a transient error that may succeed on retry.
     *
     * **Retryable exceptions:**
     * - Network timeouts
     * - No network connection
     * - HTTP 5xx errors (server errors)
     * - HTTP 408 (Request Timeout)
     * - HTTP 429 (Too Many Requests)
     * - Transient persistence errors
     *
     * **Non-retryable exceptions:**
     * - HTTP 4xx errors (client errors, except 408 and 429)
     * - Validation errors
     * - Serialization errors
     * - Configuration errors
     * - Disk full
     * - Permission denied
     * - Not found
     */
    abstract val isRetryable: Boolean

    /**
     * Network-related errors (HTTP, DNS, timeout, etc.)
     */
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
                // Retryable client errors
                408 -> true  // Request Timeout
                429 -> true  // Too Many Requests
                // Retryable server errors
                in 500..599 -> true  // All 5xx errors
                // Non-retryable client errors
                else -> false
            }
        }

        class DnsError(cause: Throwable? = null) :
            NetworkException("DNS resolution failed", cause) {
            override val isRetryable: Boolean = true
        }

        class SslError(cause: Throwable? = null) :
            NetworkException("SSL/TLS error", cause) {
            override val isRetryable: Boolean = false  // Certificate issues unlikely to resolve on retry
        }
    }

    /**
     * Persistence-related errors (disk full, permission denied, etc.)
     */
    sealed class PersistenceException(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {

        class ReadError(cause: Throwable? = null) :
            PersistenceException("Failed to read from persistence", cause) {
            override val isRetryable: Boolean = true  // May be transient (lock contention, etc.)
        }

        class WriteError(cause: Throwable? = null) :
            PersistenceException("Failed to write to persistence", cause) {
            override val isRetryable: Boolean = true  // May be transient
        }

        class DeleteError(cause: Throwable? = null) :
            PersistenceException("Failed to delete from persistence", cause) {
            override val isRetryable: Boolean = true  // May be transient
        }

        class DiskFull(cause: Throwable? = null) :
            PersistenceException("Disk is full", cause) {
            override val isRetryable: Boolean = false  // Won't resolve without user action
        }

        class PermissionDenied(cause: Throwable? = null) :
            PersistenceException("Permission denied", cause) {
            override val isRetryable: Boolean = false  // Won't resolve without user action
        }

        class TransactionConflict(cause: Throwable? = null) :
            PersistenceException("Transaction conflict", cause) {
            override val isRetryable: Boolean = true  // Optimistic locking conflict
        }

        class DatabaseLocked(cause: Throwable? = null) :
            PersistenceException("Database is locked", cause) {
            override val isRetryable: Boolean = true  // Temporary lock
        }
    }

    /**
     * Data validation errors
     */
    class ValidationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false  // Data validation won't pass on retry
    }

    /**
     * Key not found in any cache or fetcher
     */
    class NotFound(
        key: String,
        cause: Throwable? = null
    ) : StoreException("Key not found: $key", cause) {
        override val isRetryable: Boolean = false  // Entity doesn't exist
    }

    /**
     * Data deserialization errors
     */
    class SerializationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false  // Data format issue
    }

    /**
     * Configuration errors
     */
    class ConfigurationError(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = false  // Setup issue
    }

    /**
     * Rate limiting errors
     */
    class RateLimited(
        val retryAfter: Duration? = null,
        cause: Throwable? = null
    ) : StoreException("Rate limited${retryAfter?.let { " (retry after $it)" } ?: ""}", cause) {
        override val isRetryable: Boolean = true  // Can retry after backoff
    }

    /**
     * Unknown error
     */
    class Unknown(
        message: String,
        cause: Throwable? = null
    ) : StoreException(message, cause) {
        override val isRetryable: Boolean = true  // Conservative: allow retry
    }

    companion object {
        /**
         * Converts a generic exception to a StoreException.
         *
         * This provides intelligent classification of platform-specific exceptions
         * into appropriate StoreException types with correct retryability.
         *
         * **Common mappings:**
         * - IO exceptions → NetworkException or PersistenceException
         * - Serialization exceptions → SerializationError
         * - SQL exceptions → PersistenceException
         * - Timeout exceptions → NetworkException.Timeout
         * - CancellationException → rethrown (not converted)
         *
         * @param throwable The exception to convert
         * @return Appropriate StoreException subclass
         */
        fun from(throwable: Throwable): StoreException {
            // Never wrap CancellationException
            if (throwable is kotlinx.coroutines.CancellationException) {
                throw throwable
            }

            return when (throwable) {
                // Already a StoreException
                is StoreException -> throwable

                // Serialization errors
                is kotlinx.serialization.SerializationException ->
                    SerializationError("Serialization failed: ${throwable.message}", throwable)

                // Platform-specific mappings would go here
                // Note: These are commented out because they're not available in commonMain
                // Platforms should provide their own converters or use expect/actual

                /* JVM-specific (example - would need expect/actual):
                is java.io.IOException -> when {
                    throwable.message?.contains("timeout", ignoreCase = true) == true ->
                        NetworkException.Timeout(throwable)
                    throwable.message?.contains("connection", ignoreCase = true) == true ->
                        NetworkException.NoConnection(throwable)
                    else -> PersistenceException.ReadError(throwable)
                }

                is java.net.SocketTimeoutException ->
                    NetworkException.Timeout(throwable)

                is java.net.UnknownHostException ->
                    NetworkException.DnsError(throwable)

                is javax.net.ssl.SSLException ->
                    NetworkException.SslError(throwable)

                is java.sql.SQLException -> when {
                    throwable.message?.contains("disk", ignoreCase = true) == true ->
                        PersistenceException.DiskFull(throwable)
                    throwable.message?.contains("lock", ignoreCase = true) == true ->
                        PersistenceException.DatabaseLocked(throwable)
                    throwable.message?.contains("constraint", ignoreCase = true) == true ->
                        ValidationError("Constraint violation: ${throwable.message}", throwable)
                    else -> PersistenceException.WriteError(throwable)
                }
                */

                // Generic mapping based on message content
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

                    // Default to Unknown with retry allowed (conservative)
                    else -> Unknown(throwable.message ?: "Unknown error", throwable)
                }
            }
        }

        /**
         * Creates an HttpError from an HTTP status code.
         *
         * @param statusCode HTTP status code
         * @param body Optional response body
         * @param cause Optional underlying exception
         * @return NetworkException.HttpError with appropriate retryability
         */
        fun fromHttpStatus(
            statusCode: Int,
            body: String? = null,
            cause: Throwable? = null
        ): NetworkException.HttpError {
            return NetworkException.HttpError(statusCode, body, cause)
        }

        /**
         * Creates a RateLimited exception with retry-after duration.
         *
         * @param retryAfter Duration to wait before retrying
         * @param cause Optional underlying exception
         * @return RateLimited exception
         */
        fun rateLimited(retryAfter: Duration? = null, cause: Throwable? = null): RateLimited {
            return RateLimited(retryAfter, cause)
        }
    }
}
