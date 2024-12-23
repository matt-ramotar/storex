package dev.mattramotar.storex.store.internal.hooks

import dev.mattramotar.storex.mutablestore.telemetry.MutableStoreTelemetryEvent
import kotlinx.coroutines.flow.SharedFlow

interface MutableStoreFlowTelemetryHooks<Key : Any, Partial : Any, Value : Any, Error : Any> {
    val mutableStoreTelemetryEvents: SharedFlow<MutableStoreTelemetryEvent<Key, Partial, Value, Error>>
}