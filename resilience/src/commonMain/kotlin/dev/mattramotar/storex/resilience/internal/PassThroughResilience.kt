package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.LoadState
import dev.mattramotar.storex.resilience.OperationEvent
import dev.mattramotar.storex.resilience.OperationEvents
import dev.mattramotar.storex.resilience.OperationResult
import dev.mattramotar.storex.resilience.Resilience
import dev.mattramotar.storex.resilience.dsl.OperationSpecScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.cancellation.CancellationException

/** A pass-through "OFF-switch" implementation. */
internal object PassThroughResilience : Resilience {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun <T> execute(
        configure: OperationSpecScope<T>.() -> Unit
    ): OperationResult<T> {
        val specScope = RealOperationSpecScope<T>().apply(configure)
        val spec = specScope.build()
        return try {
            val value = spec.call.invoke()
            OperationResult.Success(value)
        } catch (error: TimeoutCancellationException) {
            OperationResult.Failure.Error(error)
        } catch (_: CancellationException) {
            OperationResult.Failure.Cancelled
        } catch (error: Throwable) {
            OperationResult.Failure.Error(error)
        }
    }

    override fun <T> asLoadStateFlow(
        configure: OperationSpecScope<T>.() -> Unit
    ): Flow<LoadState<T>> =
        flow {
            emit(LoadState.Loading)
            when (val outcome = execute(configure)) {
                is OperationResult.Success -> emit(LoadState.Success(outcome.value))
                is OperationResult.Failure.Cancelled -> emit(LoadState.Error(CancellationException()))
                is OperationResult.Failure -> {
                    val exception =
                        if (outcome is OperationResult.Failure.Error) outcome.throwable
                        else IllegalStateException("Unexpected failure: $outcome")
                    emit(LoadState.Error(exception))
                }
            }
        }

    override val operationEvents: OperationEvents =
        object : OperationEvents {
            override val events: SharedFlow<OperationEvent> = MutableSharedFlow(replay = 0)
        }
}
