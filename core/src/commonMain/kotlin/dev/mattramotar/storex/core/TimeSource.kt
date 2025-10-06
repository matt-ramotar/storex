package dev.mattramotar.storex.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Abstraction over time to allow tests to control the clock.
 *
 * This follows the same pattern as [dev.mattramotar.storex.resilience.Clock] for delays,
 * but provides access to the current instant rather than suspending delays.
 *
 * Production code uses [SYSTEM] which delegates to [Clock.System.now], while tests
 * can inject a mutable time source to control time progression without waiting.
 *
 * Example:
 * ```kotlin
 * // Production
 * val store = store<UserKey, User> {
 *     fetcher { api.getUser(it.id) }
 *     cache { ttl = 5.minutes }
 *     // Uses TimeSource.SYSTEM by default
 * }
 *
 * // Tests
 * val testTime = TestTimeSource.atNow()
 * val store = store<UserKey, User>(timeSource = testTime) {
 *     fetcher { api.getUser(it.id) }
 *     cache { ttl = 5.minutes }
 * }
 * testTime.advance(10.minutes) // Simulate time passing
 * ```
 */
fun interface TimeSource {
    /**
     * Returns the current instant in time.
     *
     * Production implementations return the actual current time,
     * while test implementations can return a controllable virtual time.
     */
    fun now(): Instant

    companion object {
        /**
         * System time source that delegates to [Clock.System.now].
         *
         * This is the default time source used in production code.
         */
        val SYSTEM: TimeSource = TimeSource { Clock.System.now() }
    }
}
