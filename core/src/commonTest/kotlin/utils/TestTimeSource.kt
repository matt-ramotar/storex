package dev.mattramotar.storex.core.utils

import dev.mattramotar.storex.core.TimeSource
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Mutable time source for tests.
 *
 * Allows controlling time to test TTL expiration, freshness policies, and other time-dependent behavior
 * without actually waiting for real time to pass.
 *
 * Example:
 * ```kotlin
 * @Test
 * fun cache_whenExpired_thenReturnsNull() = runTest {
 *     val timeSource = TestTimeSource.atNow()
 *     val cache = MemoryCacheImpl(maxSize = 100, ttl = 5.minutes, timeSource = timeSource)
 *
 *     cache.put(key, value)
 *     timeSource.advance(6.minutes) // Simulate 6 minutes passing
 *
 *     assertNull(cache.get(key)) // Entry expired
 * }
 * ```
 */
class TestTimeSource(
    private var currentTime: Instant = Instant.fromEpochMilliseconds(1700000000000) // 2023-11-14 22:13:20 UTC
) : TimeSource {

    override fun now(): Instant = currentTime

    /**
     * Advance time by the given duration.
     *
     * @param duration How much time to advance
     */
    fun advance(duration: Duration) {
        currentTime = currentTime.plus(duration)
    }

    /**
     * Set time to a specific instant.
     *
     * @param time The new current time
     */
    fun setTime(time: Instant) {
        currentTime = time
    }

    /**
     * Reset to initial time.
     */
    fun reset() {
        currentTime = Instant.fromEpochMilliseconds(1700000000000)
    }

    companion object {
        /**
         * Create a time source starting at epoch (1970-01-01T00:00:00Z).
         */
        fun atEpoch(): TestTimeSource {
            return TestTimeSource(Instant.fromEpochMilliseconds(0))
        }

        /**
         * Create a time source starting at a reasonable "now" time for tests (2023-11-14).
         */
        fun atNow(): TestTimeSource {
            return TestTimeSource()
        }

        /**
         * Create a time source starting at a specific epoch second.
         *
         * @param epochSeconds The epoch second to start at
         */
        fun at(epochSeconds: Long): TestTimeSource {
            return TestTimeSource(Instant.fromEpochSeconds(epochSeconds))
        }
    }
}
