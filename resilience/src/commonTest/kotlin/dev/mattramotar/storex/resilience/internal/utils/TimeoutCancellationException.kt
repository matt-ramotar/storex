package dev.mattramotar.storex.resilience.internal.utils

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal suspend fun TimeoutCancellationException(): TimeoutCancellationException {
    val timeoutCancellationException = try {
        withTimeout(0) {
            delay(1)
            null
        }
    } catch (e: TimeoutCancellationException) {
        e
    }

    return requireNotNull(timeoutCancellationException)
}