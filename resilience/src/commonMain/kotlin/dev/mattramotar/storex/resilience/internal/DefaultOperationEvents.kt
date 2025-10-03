package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.OperationEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Default, thread-safe bus for resilience events. Uses an extra buffer and
 * [BufferOverflow.DROP_OLDEST] overflow policy to avoid backpressure or suspension of event
 * publishers.
 */
internal class DefaultOperationEvents(extraBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY) :
    MutableOperationEvents {
    override val events: MutableSharedFlow<OperationEvent> =
        MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = extraBufferCapacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    companion object {
        private const val DEFAULT_BUFFER_CAPACITY = 64
    }
}
