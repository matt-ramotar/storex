package dev.mattramotar.storex.core.utils

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Test clock for controlling time in tests.
 *
 * Allows advancing time manually to test time-dependent behavior like TTL expiration.
 */
class TestClock(private var currentTime: Instant = Instant.fromEpochMilliseconds(0)) {

    /**
     * Get the current time.
     */
    fun now(): Instant = currentTime

    /**
     * Advance time by the given duration.
     */
    fun advance(duration: Duration) {
        currentTime = currentTime.plus(duration)
    }

    /**
     * Set time to a specific instant.
     */
    fun setTime(time: Instant) {
        currentTime = time
    }

    /**
     * Reset to epoch.
     */
    fun reset() {
        currentTime = Instant.fromEpochMilliseconds(0)
    }

    companion object {
        /**
         * Create a clock starting at a specific epoch second.
         */
        fun at(epochSeconds: Long): TestClock {
            return TestClock(Instant.fromEpochSeconds(epochSeconds))
        }

        /**
         * Create a clock at epoch 0.
         */
        fun atEpoch(): TestClock {
            return TestClock(Instant.fromEpochMilliseconds(0))
        }

        /**
         * Create a clock at a reasonable "now" time for tests.
         */
        fun atNow(): TestClock {
            return TestClock(Instant.fromEpochSeconds(1_700_000_000)) // 2023-11-14
        }
    }
}
