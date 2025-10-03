package dev.mattramotar.storex.resilience

import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * Abstraction over suspending delays so production code can keep real-time behavior while tests
 * swap in a virtual-time implementation.
 */
fun interface Clock {

  /** Suspends the current coroutine for [duration]. */
  suspend fun sleep(duration: Duration)

  /** Companion object for [Clock]. */
  companion object {
    /** System [Clock] that delegates to [delay]. */
    val SYSTEM: Clock = Clock { delay(it) }
  }
}
