package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.Clock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration

/**
 * Records every [Clock.sleep] invocation for later assertions.
 *
 * This test double records sleep requests but suspends indefinitely, preventing
 * the coroutine from continuing. This allows tests to verify that timers were
 * scheduled without the timer actually completing and triggering state transitions.
 */
internal class RecordingTestClock : Clock {

    /** Count of [sleep] invocations. */
    val sleeps: MutableList<Duration> = mutableListOf()

    override suspend fun sleep(duration: Duration) {
        sleeps += duration
        suspendCancellableCoroutine<Unit> { }
    }
}
