package dev.mattramotar.storex.mutablestore.telemetry

/**
 * A telemetry interface that advanced users can implement
 * to collect logs, metrics, or forward events to analytics.
 */
interface MutableStoreTelemetry<Key : Any, Partial: Any, Value : Any, Error: Any> {
    /**
     * Called each time an event is triggered.
     */
    fun onEvent(event: MutableStoreTelemetryEvent<Key, Partial, Value, Error>)
}

