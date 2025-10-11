package dev.mattramotar.storex.core.internal

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

class StoreExceptionTest {

    // NetworkException.Timeout tests

    @Test
    fun timeout_givenNoCause_thenIsRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.Timeout()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Network timeout", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun timeout_givenCause_thenIsRetryable() {
        // Given
        val cause = RuntimeException("Original error")

        // When
        val exception = StoreException.NetworkException.Timeout(cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Network timeout", exception.message)
        assertSame(cause, exception.cause)
    }

    // NetworkException.NoConnection tests

    @Test
    fun noConnection_givenNoCause_thenIsRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.NoConnection()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("No network connection", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun noConnection_givenCause_thenIsRetryable() {
        // Given
        val cause = RuntimeException("Connection failed")

        // When
        val exception = StoreException.NetworkException.NoConnection(cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("No network connection", exception.message)
        assertSame(cause, exception.cause)
    }

    // NetworkException.HttpError tests

    @Test
    fun httpError_given408_thenIsRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.HttpError(408)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("HTTP 408", exception.message)
        assertEquals(408, exception.statusCode)
        assertNull(exception.body)
        assertNull(exception.cause)
    }

    @Test
    fun httpError_given429_thenIsRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.HttpError(429)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("HTTP 429", exception.message)
        assertEquals(429, exception.statusCode)
    }

    @Test
    fun httpError_given500_thenIsRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.HttpError(500)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("HTTP 500", exception.message)
        assertEquals(500, exception.statusCode)
    }

    @Test
    fun httpError_given503_thenIsRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.HttpError(503)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("HTTP 503", exception.message)
    }

    @Test
    fun httpError_given599_thenIsRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.HttpError(599)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("HTTP 599", exception.message)
    }

    @Test
    fun httpError_given400_thenIsNotRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.HttpError(400)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("HTTP 400", exception.message)
    }

    @Test
    fun httpError_given404_thenIsNotRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.HttpError(404)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("HTTP 404", exception.message)
    }

    @Test
    fun httpError_given200_thenIsNotRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.HttpError(200)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("HTTP 200", exception.message)
    }

    @Test
    fun httpError_givenBodyAndCause_thenStoresAll() {
        // Given
        val statusCode = 500
        val body = """{"error": "Internal Server Error"}"""
        val cause = RuntimeException("Server down")

        // When
        val exception = StoreException.NetworkException.HttpError(statusCode, body, cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("HTTP 500", exception.message)
        assertEquals(statusCode, exception.statusCode)
        assertEquals(body, exception.body)
        assertSame(cause, exception.cause)
    }

    // NetworkException.DnsError tests

    @Test
    fun dnsError_givenNoCause_thenIsRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.DnsError()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("DNS resolution failed", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun dnsError_givenCause_thenIsRetryable() {
        // Given
        val cause = RuntimeException("Host not found")

        // When
        val exception = StoreException.NetworkException.DnsError(cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("DNS resolution failed", exception.message)
        assertSame(cause, exception.cause)
    }

    // NetworkException.SslError tests

    @Test
    fun sslError_givenNoCause_thenIsNotRetryable() {
        // Given / When
        val exception = StoreException.NetworkException.SslError()

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("SSL/TLS error", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun sslError_givenCause_thenIsNotRetryable() {
        // Given
        val cause = RuntimeException("Certificate invalid")

        // When
        val exception = StoreException.NetworkException.SslError(cause)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("SSL/TLS error", exception.message)
        assertSame(cause, exception.cause)
    }

    // PersistenceException.ReadError tests

    @Test
    fun readError_givenNoCause_thenIsRetryable() {
        // Given / When
        val exception = StoreException.PersistenceException.ReadError()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Failed to read from persistence", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun readError_givenCause_thenIsRetryable() {
        // Given
        val cause = RuntimeException("File locked")

        // When
        val exception = StoreException.PersistenceException.ReadError(cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Failed to read from persistence", exception.message)
        assertSame(cause, exception.cause)
    }

    // PersistenceException.WriteError tests

    @Test
    fun writeError_givenNoCause_thenIsRetryable() {
        // Given / When
        val exception = StoreException.PersistenceException.WriteError()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Failed to write to persistence", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun writeError_givenCause_thenIsRetryable() {
        // Given
        val cause = RuntimeException("Write conflict")

        // When
        val exception = StoreException.PersistenceException.WriteError(cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Failed to write to persistence", exception.message)
        assertSame(cause, exception.cause)
    }

    // PersistenceException.DeleteError tests

    @Test
    fun deleteError_givenNoCause_thenIsRetryable() {
        // Given / When
        val exception = StoreException.PersistenceException.DeleteError()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Failed to delete from persistence", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun deleteError_givenCause_thenIsRetryable() {
        // Given
        val cause = RuntimeException("Delete failed")

        // When
        val exception = StoreException.PersistenceException.DeleteError(cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Failed to delete from persistence", exception.message)
        assertSame(cause, exception.cause)
    }

    // PersistenceException.DiskFull tests

    @Test
    fun diskFull_givenNoCause_thenIsNotRetryable() {
        // Given / When
        val exception = StoreException.PersistenceException.DiskFull()

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("Disk is full", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun diskFull_givenCause_thenIsNotRetryable() {
        // Given
        val cause = RuntimeException("No space left")

        // When
        val exception = StoreException.PersistenceException.DiskFull(cause)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("Disk is full", exception.message)
        assertSame(cause, exception.cause)
    }

    // PersistenceException.PermissionDenied tests

    @Test
    fun permissionDenied_givenNoCause_thenIsNotRetryable() {
        // Given / When
        val exception = StoreException.PersistenceException.PermissionDenied()

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("Permission denied", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun permissionDenied_givenCause_thenIsNotRetryable() {
        // Given
        val cause = RuntimeException("Access denied")

        // When
        val exception = StoreException.PersistenceException.PermissionDenied(cause)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("Permission denied", exception.message)
        assertSame(cause, exception.cause)
    }

    // PersistenceException.TransactionConflict tests

    @Test
    fun transactionConflict_givenNoCause_thenIsRetryable() {
        // Given / When
        val exception = StoreException.PersistenceException.TransactionConflict()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Transaction conflict", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun transactionConflict_givenCause_thenIsRetryable() {
        // Given
        val cause = RuntimeException("Optimistic lock conflict")

        // When
        val exception = StoreException.PersistenceException.TransactionConflict(cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Transaction conflict", exception.message)
        assertSame(cause, exception.cause)
    }

    // PersistenceException.DatabaseLocked tests

    @Test
    fun databaseLocked_givenNoCause_thenIsRetryable() {
        // Given / When
        val exception = StoreException.PersistenceException.DatabaseLocked()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Database is locked", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun databaseLocked_givenCause_thenIsRetryable() {
        // Given
        val cause = RuntimeException("DB locked")

        // When
        val exception = StoreException.PersistenceException.DatabaseLocked(cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Database is locked", exception.message)
        assertSame(cause, exception.cause)
    }

    // ValidationError tests

    @Test
    fun validationError_givenMessage_thenIsNotRetryable() {
        // Given
        val message = "Field must not be empty"

        // When
        val exception = StoreException.ValidationError(message)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals(message, exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun validationError_givenMessageAndCause_thenIsNotRetryable() {
        // Given
        val message = "Invalid email format"
        val cause = IllegalArgumentException("Email validation failed")

        // When
        val exception = StoreException.ValidationError(message, cause)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals(message, exception.message)
        assertSame(cause, exception.cause)
    }

    // NotFound tests

    @Test
    fun notFound_givenKey_thenIsNotRetryable() {
        // Given
        val key = "user-123"

        // When
        val exception = StoreException.NotFound(key)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("Key not found: $key", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun notFound_givenKeyAndCause_thenIsNotRetryable() {
        // Given
        val key = "product-456"
        val cause = RuntimeException("Lookup failed")

        // When
        val exception = StoreException.NotFound(key, cause)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals("Key not found: $key", exception.message)
        assertSame(cause, exception.cause)
    }

    // SerializationError tests

    @Test
    fun serializationError_givenMessage_thenIsNotRetryable() {
        // Given
        val message = "Failed to deserialize JSON"

        // When
        val exception = StoreException.SerializationError(message)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals(message, exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun serializationError_givenMessageAndCause_thenIsNotRetryable() {
        // Given
        val message = "Invalid JSON format"
        val cause = RuntimeException("Parse error at line 5")

        // When
        val exception = StoreException.SerializationError(message, cause)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals(message, exception.message)
        assertSame(cause, exception.cause)
    }

    // ConfigurationError tests

    @Test
    fun configurationError_givenMessage_thenIsNotRetryable() {
        // Given
        val message = "Missing API key"

        // When
        val exception = StoreException.ConfigurationError(message)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals(message, exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun configurationError_givenMessageAndCause_thenIsNotRetryable() {
        // Given
        val message = "Invalid configuration"
        val cause = IllegalStateException("Config file not found")

        // When
        val exception = StoreException.ConfigurationError(message, cause)

        // Then
        assertFalse(exception.isRetryable)
        assertEquals(message, exception.message)
        assertSame(cause, exception.cause)
    }

    // RateLimited tests

    @Test
    fun rateLimited_givenNoRetryAfter_thenIsRetryable() {
        // Given / When
        val exception = StoreException.RateLimited()

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Rate limited", exception.message)
        assertNull(exception.retryAfter)
        assertNull(exception.cause)
    }

    @Test
    fun rateLimited_givenRetryAfter_thenIsRetryableWithDuration() {
        // Given
        val retryAfter = 5.minutes

        // When
        val exception = StoreException.RateLimited(retryAfter)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Rate limited (retry after $retryAfter)", exception.message)
        assertEquals(retryAfter, exception.retryAfter)
        assertNull(exception.cause)
    }

    @Test
    fun rateLimited_givenRetryAfterAndCause_thenIsRetryableWithAll() {
        // Given
        val retryAfter = 10.minutes
        val cause = RuntimeException("Too many requests")

        // When
        val exception = StoreException.RateLimited(retryAfter, cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals("Rate limited (retry after $retryAfter)", exception.message)
        assertEquals(retryAfter, exception.retryAfter)
        assertSame(cause, exception.cause)
    }

    // Unknown tests

    @Test
    fun unknown_givenMessage_thenIsRetryable() {
        // Given
        val message = "Something went wrong"

        // When
        val exception = StoreException.Unknown(message)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals(message, exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun unknown_givenMessageAndCause_thenIsRetryable() {
        // Given
        val message = "Unexpected error"
        val cause = RuntimeException("Root cause")

        // When
        val exception = StoreException.Unknown(message, cause)

        // Then
        assertTrue(exception.isRetryable)
        assertEquals(message, exception.message)
        assertSame(cause, exception.cause)
    }

    // Companion.from() tests - CancellationException

    @Test
    fun from_givenCancellationException_thenRethrows() {
        // Given
        val cancellation = CancellationException("Coroutine cancelled")

        // When / Then
        try {
            StoreException.from(cancellation)
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertSame(cancellation, e)
        }
    }

    // Companion.from() tests - StoreException

    @Test
    fun from_givenStoreException_thenReturnsSame() {
        // Given
        val storeException = StoreException.NetworkException.Timeout()

        // When
        val result = StoreException.from(storeException)

        // Then
        assertSame(storeException, result)
    }

    // Companion.from() tests - SerializationException

    @Test
    fun from_givenSerializationException_thenReturnsSerializationError() {
        // Given
        val serializationException = kotlinx.serialization.SerializationException("JSON parse error")

        // When
        val result = StoreException.from(serializationException)

        // Then
        assertIs<StoreException.SerializationError>(result)
        assertEquals("Serialization failed: JSON parse error", result.message)
        assertSame(serializationException, result.cause)
    }

    // Companion.from() tests - Message-based mapping

    @Test
    fun from_givenTimeoutMessage_thenReturnsTimeout() {
        // Given
        val throwable = RuntimeException("Connection timeout occurred")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.NetworkException.Timeout>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenConnectionMessage_thenReturnsNoConnection() {
        // Given
        val throwable = RuntimeException("No connection available")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.NetworkException.NoConnection>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenNotFoundMessage_thenReturnsNotFound() {
        // Given
        val throwable = RuntimeException("Resource not found")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.NotFound>(result)
        assertEquals("Key not found: Resource not found", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenPermissionMessage_thenReturnsPermissionDenied() {
        // Given
        val throwable = RuntimeException("Permission denied to access file")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.PersistenceException.PermissionDenied>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenAccessDeniedMessage_thenReturnsPermissionDenied() {
        // Given
        val throwable = RuntimeException("Access denied")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.PersistenceException.PermissionDenied>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenDiskFullMessage_thenReturnsDiskFull() {
        // Given
        val throwable = RuntimeException("Disk full: cannot write")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.PersistenceException.DiskFull>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenNoSpaceMessage_thenReturnsDiskFull() {
        // Given
        val throwable = RuntimeException("No space left on device")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.PersistenceException.DiskFull>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenRateLimitMessage_thenReturnsRateLimited() {
        // Given
        val throwable = RuntimeException("Rate limit exceeded")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.RateLimited>(result)
        assertNull(result.retryAfter)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenTooManyRequestsMessage_thenReturnsRateLimited() {
        // Given
        val throwable = RuntimeException("Too many requests")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.RateLimited>(result)
        assertNull(result.retryAfter)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenValidationMessage_thenReturnsValidationError() {
        // Given
        val throwable = RuntimeException("Validation failed for field email")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.ValidationError>(result)
        assertEquals("Validation failed for field email", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenInvalidMessage_thenReturnsValidationError() {
        // Given
        val throwable = RuntimeException("Invalid input data")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.ValidationError>(result)
        assertEquals("Invalid input data", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenSerializationMessage_thenReturnsSerializationError() {
        // Given
        val throwable = RuntimeException("Serialization error occurred")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.SerializationError>(result)
        assertEquals("Serialization error occurred", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenDeserializationMessage_thenReturnsSerializationError() {
        // Given
        val throwable = RuntimeException("Deserialization failed")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.SerializationError>(result)
        assertEquals("Deserialization failed", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenParseMessage_thenReturnsSerializationError() {
        // Given
        val throwable = RuntimeException("Parse error in JSON")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.SerializationError>(result)
        assertEquals("Parse error in JSON", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenConfigurationMessage_thenReturnsConfigurationError() {
        // Given
        val throwable = RuntimeException("Configuration error: missing key")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.ConfigurationError>(result)
        assertEquals("Configuration error: missing key", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenMisconfiguredMessage_thenReturnsConfigurationError() {
        // Given
        val throwable = RuntimeException("System is misconfigured")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.ConfigurationError>(result)
        assertEquals("System is misconfigured", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenLockMessage_thenReturnsDatabaseLocked() {
        // Given
        val throwable = RuntimeException("Database is locked")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.PersistenceException.DatabaseLocked>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenConflictMessage_thenReturnsTransactionConflict() {
        // Given
        val throwable = RuntimeException("Transaction conflict detected")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.PersistenceException.TransactionConflict>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenDnsMessage_thenReturnsDnsError() {
        // Given
        val throwable = RuntimeException("DNS lookup failed")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.NetworkException.DnsError>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenSslMessage_thenReturnsSslError() {
        // Given
        val throwable = RuntimeException("SSL handshake failed")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.NetworkException.SslError>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenTlsMessage_thenReturnsSslError() {
        // Given
        val throwable = RuntimeException("TLS error occurred")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.NetworkException.SslError>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenCertificateMessage_thenReturnsSslError() {
        // Given
        val throwable = RuntimeException("Certificate verification failed")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.NetworkException.SslError>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenUnknownMessage_thenReturnsUnknown() {
        // Given
        val throwable = RuntimeException("Some random error")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.Unknown>(result)
        assertEquals("Some random error", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenNullMessage_thenReturnsUnknownWithDefaultMessage() {
        // Given
        val throwable = RuntimeException(null as String?)

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.Unknown>(result)
        assertEquals("Unknown error", result.message)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenCaseInsensitiveTimeout_thenReturnsTimeout() {
        // Given
        val throwable = RuntimeException("TIMEOUT occurred")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.NetworkException.Timeout>(result)
        assertSame(throwable, result.cause)
    }

    @Test
    fun from_givenCaseInsensitivePermission_thenReturnsPermissionDenied() {
        // Given
        val throwable = RuntimeException("PERMISSION denied")

        // When
        val result = StoreException.from(throwable)

        // Then
        assertIs<StoreException.PersistenceException.PermissionDenied>(result)
        assertSame(throwable, result.cause)
    }

    // Companion.fromHttpStatus() tests

    @Test
    fun fromHttpStatus_givenStatusCode_thenReturnsHttpError() {
        // Given
        val statusCode = 404

        // When
        val result = StoreException.fromHttpStatus(statusCode)

        // Then
        assertIs<StoreException.NetworkException.HttpError>(result)
        assertEquals(statusCode, result.statusCode)
        assertNull(result.body)
        assertNull(result.cause)
    }

    @Test
    fun fromHttpStatus_givenStatusCodeAndBody_thenReturnsHttpError() {
        // Given
        val statusCode = 500
        val body = """{"error": "Internal Server Error"}"""

        // When
        val result = StoreException.fromHttpStatus(statusCode, body)

        // Then
        assertIs<StoreException.NetworkException.HttpError>(result)
        assertEquals(statusCode, result.statusCode)
        assertEquals(body, result.body)
        assertNull(result.cause)
    }

    @Test
    fun fromHttpStatus_givenAllParameters_thenReturnsHttpError() {
        // Given
        val statusCode = 503
        val body = """{"error": "Service Unavailable"}"""
        val cause = RuntimeException("Server down")

        // When
        val result = StoreException.fromHttpStatus(statusCode, body, cause)

        // Then
        assertIs<StoreException.NetworkException.HttpError>(result)
        assertEquals(statusCode, result.statusCode)
        assertEquals(body, result.body)
        assertSame(cause, result.cause)
    }

    // Companion.rateLimited() tests

    @Test
    fun rateLimited_givenNoParameters_thenReturnsRateLimited() {
        // Given / When
        val result = StoreException.rateLimited()

        // Then
        assertIs<StoreException.RateLimited>(result)
        assertNull(result.retryAfter)
        assertNull(result.cause)
    }

    @Test
    fun rateLimited_givenRetryAfter_thenReturnsRateLimitedWithDuration() {
        // Given
        val retryAfter = 5.minutes

        // When
        val result = StoreException.rateLimited(retryAfter)

        // Then
        assertIs<StoreException.RateLimited>(result)
        assertEquals(retryAfter, result.retryAfter)
        assertNull(result.cause)
    }

    @Test
    fun rateLimited_givenRetryAfterAndCause_thenReturnsRateLimitedWithAll() {
        // Given
        val retryAfter = 10.minutes
        val cause = RuntimeException("Rate limit exceeded")

        // When
        val result = StoreException.rateLimited(retryAfter, cause)

        // Then
        assertIs<StoreException.RateLimited>(result)
        assertEquals(retryAfter, result.retryAfter)
        assertSame(cause, result.cause)
    }

    @Test
    fun rateLimited_givenOnlyCause_thenReturnsRateLimitedWithCause() {
        // Given
        val cause = RuntimeException("API limit reached")

        // When
        val result = StoreException.rateLimited(null, cause)

        // Then
        assertIs<StoreException.RateLimited>(result)
        assertNull(result.retryAfter)
        assertSame(cause, result.cause)
    }
}
