package dev.mattramotar.storex.resilience

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.SharedFlow

/** Bus for all [OperationEvent]s. */
interface OperationEvents {
  /**
   * Hot, unbounded stream of resilience events. The default implementation uses an extra buffer of
   * 64 and [BufferOverflow.DROP_OLDEST] overflow policy to avoid backpressure or suspension of
   * event publishers.
   */
  val events: SharedFlow<OperationEvent>
}
