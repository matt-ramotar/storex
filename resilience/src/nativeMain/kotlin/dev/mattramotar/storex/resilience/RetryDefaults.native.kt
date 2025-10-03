package dev.mattramotar.storex.resilience

import kotlinx.coroutines.TimeoutCancellationException

/** Retry defaults for the Resilience library. */
actual object RetryDefaults {
  /** Returns whether the error should be retried. */
  actual fun retryOn(error: Throwable): Boolean {
    return when (error) {
      is TimeoutCancellationException -> true
      else -> false
    }
  }
}
