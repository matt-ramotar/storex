package dev.mattramotar.storex.resilience

import kotlinx.coroutines.TimeoutCancellationException

/**
 * Represents the result of a resilient call.
 *
 * @param T The type of the successful result.
 */
sealed interface OperationResult<out T> {

  /**
   * The call completed successfully.
   *
   * @property value The result value.
   */
  data class Success<T>(val value: T) : OperationResult<T>

  /** The operation failed. */
  sealed interface Failure : OperationResult<Nothing> {

    /** The circuit breaker was open before the first attempt. */
    data object CircuitOpen : Failure

    /**
     * The call timed out after all retries.
     *
     * @property attemptCount The number of attempts made.
     * @property cause The timeout exception that occurred.
     */
    data class TimedOut(
        val attemptCount: Int,
        val cause: TimeoutCancellationException,
    ) : Failure

    /** The operation was cancelled externally. */
    data object Cancelled : Failure

    /**
     * The call failed due to an unexpected error.
     *
     * @property throwable The underlying exception.
     */
    data class Error(val throwable: Throwable) : Failure
  }
}
