package dev.mattramotar.storex.resilience

/** Retry defaults for the Resilience library. */
expect object RetryDefaults {
  /** Returns whether the error should be retried. */
  fun retryOn(error: Throwable): Boolean
}
