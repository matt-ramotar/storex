package dev.mattramotar.storex.resilience.internal.utils

import dev.mattramotar.storex.resilience.LoadState
import dev.mattramotar.storex.resilience.OperationEvent
import dev.mattramotar.storex.resilience.OperationEvents
import dev.mattramotar.storex.resilience.OperationResult
import dev.mattramotar.storex.resilience.Resilience
import dev.mattramotar.storex.resilience.dsl.OperationSpecScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class RecordingResilience : Resilience {

    override val operationEvents: OperationEvents = object : OperationEvents {
        override val events: MutableSharedFlow<OperationEvent> = MutableSharedFlow()
    }

    var lastScope: RecordingOperationSpecScope<*>? = null
        private set

    override suspend fun <T> execute(configure: OperationSpecScope<T>.() -> Unit): OperationResult<T> {
        val scope = RecordingOperationSpecScope<T>().apply(configure)
        lastScope = scope
        val value = scope.requireCall()
        return OperationResult.Success(value)
    }

    override fun <T> asLoadStateFlow(configure: OperationSpecScope<T>.() -> Unit): Flow<LoadState<T>> {
        return kotlinx.coroutines.flow.flow {
            val scope = RecordingOperationSpecScope<T>().apply(configure)
            lastScope = scope
            emit(LoadState.Loading)
            val value = scope.requireCall()
            emit(LoadState.Success(value))
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> lastScopeTyped(): RecordingOperationSpecScope<T> {
        val scope = lastScope as? RecordingOperationSpecScope<T>
        return scope ?: error("Expected RecordingOperationSpecScope<${T::class}> but was $lastScope")
    }
}
