package dev.mattramotar.storex.resilience

import kotlin.time.Duration

/**
 * Emitted whenever [Resilience.execute] schedules a retry.
 *
 * @param attempt A zero-based retry count.
 * @param delay How long to wait before the next attempt.
 * @param cause The error that triggered this retry.
 */
data class RetryScheduled(
    val attempt: Int,
    val delay: Duration,
    val cause: Throwable,
) : OperationEvent
