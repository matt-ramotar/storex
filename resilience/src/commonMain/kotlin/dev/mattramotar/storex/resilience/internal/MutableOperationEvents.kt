package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.OperationEvent
import dev.mattramotar.storex.resilience.OperationEvents
import kotlinx.coroutines.flow.MutableSharedFlow

/** Bus for all [OperationEvent]s. */
internal interface MutableOperationEvents : OperationEvents {
    /** Hot, unbounded stream of resilience events. */
    override val events: MutableSharedFlow<OperationEvent>
}
