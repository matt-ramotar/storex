package dev.mattramotar.storex.store.internal.hooks

import dev.mattramotar.storex.mutablestore.telemetry.StoreTelemetryEvent
import kotlinx.coroutines.flow.SharedFlow

interface StoreFlowTelemetryHooks<Key : Any, Value : Any> {
    val storeFlowTelemetryEvents: SharedFlow<StoreTelemetryEvent<Key, Value>>
}

