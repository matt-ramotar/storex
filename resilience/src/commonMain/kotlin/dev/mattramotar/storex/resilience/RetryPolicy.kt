package dev.mattramotar.storex.resilience

import kotlin.jvm.JvmStatic
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Defines a policy that takes in a retry attempt index and returns a delay before the next try or
 * `null` to stop retrying.
 */
fun interface RetryPolicy {

  /**
   * Decides if and how long to wait before the next retry.
   *
   * @param attempt A zero-based retry index.
   * @return The backoff [Duration], or `null` to stop retrying.
   */
  fun nextDelay(attempt: Int): Duration?

  /** Companion object for [RetryPolicy]. */
  companion object {

    /** Maximum exponent to cap backoff growth and prevent overflow (2^30 ≈ 1 billion). */
    private const val MAX_EXPONENT = 30

    /** Never retry. */
    val NONE: RetryPolicy = RetryPolicy { null }

    /**
     * Exponential backoff with full jitter.
     *
     * @param maxRetries The maximum number of retries.
     * @param baseDelay The initial delay before first retry.
     * @param maxDelay The maximum for any single delay.
     * @param random A [Random] source.
     */
    @JvmStatic
    fun exponentialJitter(
        maxRetries: Int = 2,
        baseDelay: Duration = 200.milliseconds,
        maxDelay: Duration = 30.seconds,
        random: Random = Random,
    ): RetryPolicy = RetryPolicy { attempt ->
      if (attempt >= maxRetries) {
        null
      } else {
        val exponent = minOf(attempt, MAX_EXPONENT)
        val backoffFactor = 1L shl exponent
        val calculatedDelay = baseDelay.inWholeMilliseconds * backoffFactor
        val upperBound = calculatedDelay.coerceAtMost(maxDelay.inWholeMilliseconds)
        val sleep = random.nextLongInclusive(0, upperBound)
        sleep.milliseconds
      }
    }

    /** Inclusive variant of [Random.nextLong]. */
    private fun Random.nextLongInclusive(from: Long, untilInclusive: Long): Long =
        if (untilInclusive == Long.MAX_VALUE) nextLong(from, Long.MAX_VALUE)
        else nextLong(from, untilInclusive + 1)
  }
}
