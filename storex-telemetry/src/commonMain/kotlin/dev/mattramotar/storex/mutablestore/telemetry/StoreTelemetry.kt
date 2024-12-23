package dev.mattramotar.storex.mutablestore.telemetry

/**
 * A telemetry interface that advanced users can implement
 * to collect logs, metrics, or forward events to analytics.
 */
interface StoreTelemetry<Key : Any, Value : Any> {
    /**
     * Called each time an event is triggered.
     */
    fun onEvent(event: StoreTelemetryEvent<Key, Value>)
}

