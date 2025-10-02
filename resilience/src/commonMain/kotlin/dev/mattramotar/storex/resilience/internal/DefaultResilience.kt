package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.CircuitBreakerOpenException
import dev.mattramotar.storex.resilience.Clock
import dev.mattramotar.storex.resilience.LoadState
import dev.mattramotar.storex.resilience.OperationEvents
import dev.mattramotar.storex.resilience.OperationResult
import dev.mattramotar.storex.resilience.Resilience
import dev.mattramotar.storex.resilience.RetryScheduled
import dev.mattramotar.storex.resilience.dsl.OperationSpecScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/**
 * Default implementation of [Resilience].
 *
 * @param clock A [Clock] for sleeping.
 * @param operationEventsFactory A factory for creating a [MutableOperationEvents] for publishing
 * resilience events.
 */
internal class DefaultResilience(
    private val clock: Clock = Clock.SYSTEM,
    operationEventsFactory: () -> MutableOperationEvents = { DefaultOperationEvents() }
) : Resilience {

    private val mutableOperationEvents: MutableOperationEvents = operationEventsFactory()
    override val operationEvents: OperationEvents = mutableOperationEvents

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override suspend fun <T> execute(
        configure: OperationSpecScope<T>.() -> Unit
    ): OperationResult<T> {
        val specScope = RealOperationSpecScope<T>().apply(configure)
        val spec = specScope.build()

        val circuitBreaker =
            spec.circuitBreaker
                ?: DefaultCircuitBreaker(
                    mutableOperationEvents = mutableOperationEvents,
                    failureThreshold = spec.failureThreshold,
                    openTTL = spec.openTTL,
                    probeQuota = spec.probeQuota,
                    clock = clock
                )

        var attempt = 0
        if (!circuitBreaker.tryAcquire()) return OperationResult.Failure.CircuitOpen

        while (true) {
            try {
                val value = withTimeout(spec.timeout) { spec.call() }
                circuitBreaker.onSuccess()
                return OperationResult.Success(value)
            } catch (exception: TimeoutCancellationException) {
                circuitBreaker.onFailure()

                val next =
                    spec.retryPolicy.nextDelay(attempt).also { attempt++ }
                        ?: return OperationResult.Failure.TimedOut(attempt, exception)

                mutableOperationEvents.events.emit(
                    RetryScheduled(attempt = attempt - 1, delay = next, cause = exception)
                )
                circuitBreaker.onFailure()
                clock.sleep(next)
            } catch (_: CancellationException) {
                return OperationResult.Failure.Cancelled
            } catch (exception: Throwable) {
                // Decide whether we're retrying before we mutate the breaker so that the RetryScheduled
                // never competes with a CircuitBreakerEvent.
                val shouldRetry = spec.retryOn(exception)
                val next = if (shouldRetry) spec.retryPolicy.nextDelay(attempt).also { attempt++ } else null

                if (next == null) {
                    circuitBreaker.onFailure()
                    return OperationResult.Failure.Error(exception)
                }

                mutableOperationEvents.events.emit(
                    RetryScheduled(attempt = attempt - 1, delay = next, cause = exception)
                )

                circuitBreaker.onFailure()
                clock.sleep(next)
            }
        }
    }

    override fun <T> asLoadStateFlow(
        configure: OperationSpecScope<T>.() -> Unit
    ): Flow<LoadState<T>> =
        flow {
            emit(LoadState.Loading)

            when (val outcome = execute(configure)) {
                is OperationResult.Success -> emit(LoadState.Success(outcome.value))
                is OperationResult.Failure.Error -> emit(LoadState.Error(outcome.throwable))
                is OperationResult.Failure.TimedOut -> emit(LoadState.Error(outcome.cause))
                is OperationResult.Failure.CircuitOpen ->
                    emit(LoadState.Error(CircuitBreakerOpenException()))

                is OperationResult.Failure.Cancelled ->
                    emit(LoadState.Error(CancellationException("Operation cancelled.")))
            }
        }
}
