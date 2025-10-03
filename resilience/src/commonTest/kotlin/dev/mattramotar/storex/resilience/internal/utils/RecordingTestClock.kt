package dev.mattramotar.storex.resilience.internal.utils

import dev.mattramotar.storex.resilience.Clock
import kotlin.time.Duration

/**
 * Records every [Clock.sleep] invocation for later assertions.
 */
internal class RecordingTestClock : Clock {

    /** Count of [sleep] invocations. */
    val sleeps: MutableList<Duration> = mutableListOf()

    override suspend fun sleep(duration: Duration) {
        sleeps += duration
    }
}
