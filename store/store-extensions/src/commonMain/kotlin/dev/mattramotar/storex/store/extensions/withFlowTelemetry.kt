@file:Suppress("UNCHECKED_CAST")

package dev.mattramotar.storex.store.extensions

import dev.mattramotar.storex.mutablestore.telemetry.StoreTelemetryEvent
import dev.mattramotar.storex.store.core.api.Store
import dev.mattramotar.storex.store.internal.hooks.StoreFlowTelemetryHooks
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow


fun <Key : Any, Value : Any> Store<Key, Value>.telemetryEvents(): SharedFlow<StoreTelemetryEvent<Key, Value>> {

    val storeFlowTelemetryHooks = this as StoreFlowTelemetryHooks<Key, Value>
    return storeFlowTelemetryHooks.storeFlowTelemetryEvents
}

suspend fun <Key : Any, Value : Any> Store<Key, Value>.collectTelemetryEvents(
    collector: FlowCollector<StoreTelemetryEvent<Key, Value>>
) {

    val storeFlowTelemetryHooks = this as StoreFlowTelemetryHooks<Key, Value>
    storeFlowTelemetryHooks.storeFlowTelemetryEvents.collect(collector)
}



