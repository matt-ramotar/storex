@file:Suppress("UNCHECKED_CAST")

package dev.mattramotar.storex.mutablestore.core

import dev.mattramotar.storex.mutablestore.core.api.MutableStore
import dev.mattramotar.storex.mutablestore.telemetry.MutableStoreTelemetryEvent
import dev.mattramotar.storex.store.internal.hooks.MutableStoreFlowTelemetryHooks
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow

fun <Key : Any, Partial : Any, Value : Any, Error : Any> MutableStore<Key, Partial, Value, Error>.telemetryEvents(): SharedFlow<MutableStoreTelemetryEvent<Key, Partial, Value, Error>> {

    return (this as MutableStoreFlowTelemetryHooks<Key, Partial, Value, Error>).mutableStoreTelemetryEvents
}

suspend fun <Key : Any, Partial : Any, Value : Any, Error : Any> MutableStore<Key, Partial, Value, Error>.collectTelemetryEvents(
    collector: FlowCollector<MutableStoreTelemetryEvent<Key, Partial, Value, Error>>
) {
    (this as MutableStoreFlowTelemetryHooks<Key, Partial, Value, Error>).mutableStoreTelemetryEvents.collect(collector)
}