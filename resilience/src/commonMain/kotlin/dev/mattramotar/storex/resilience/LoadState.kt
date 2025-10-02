package dev.mattramotar.storex.resilience

/**
 * Represents the state of an asynchronous operation.
 *
 * @param T The type of the successful result.
 */
sealed class LoadState<out T> {
  /** The operation is in progress. */
  data object Loading : LoadState<Nothing>()

  /**
   * The operation completed successfully.
   *
   * @property data The result of the operation.
   */
  data class Success<T>(val data: T) : LoadState<T>()

  /**
   * The operation failed.
   *
   * @property throwable The error that occurred.
   */
  data class Error(val throwable: Throwable) : LoadState<Nothing>()
}
