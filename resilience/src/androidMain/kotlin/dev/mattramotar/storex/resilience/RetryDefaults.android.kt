package dev.mattramotar.storex.resilience

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.TimeoutCancellationException

/** Retry defaults for the Resilience library. */
actual object RetryDefaults {
  /** Returns whether the error should be retried. */
  actual fun retryOn(error: Throwable): Boolean {
    return when (error) {
      is TimeoutCancellationException, is SocketTimeoutException, is SSLException, is IOException ->
          true
      else -> false
    }
  }
}
