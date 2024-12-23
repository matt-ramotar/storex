package dev.mattramotar.storex.store.core

import dev.mattramotar.storex.store.core.api.MemoryPolicy
import dev.mattramotar.storex.store.core.api.SourceOfTruth
import dev.mattramotar.storex.store.core.api.Store
import dev.mattramotar.storex.store.core.impl.DefaultMemoryCache
import dev.mattramotar.storex.store.core.impl.RealStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StoreBuilder<Key : Any, Value : Any>(
    private var fetcher: suspend (Key) -> Value?,
    private var sourceOfTruth: SourceOfTruth<Key, Value>? = null,
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private var memoryPolicy: MemoryPolicy<Key, Value>? = null
) {

    fun sourceOfTruth(soT: SourceOfTruth<Key, Value>): StoreBuilder<Key, Value> {
        this.sourceOfTruth = soT
        return this
    }

    fun scope(scope: CoroutineScope): StoreBuilder<Key, Value> {
        this.scope = scope
        return this
    }

    fun memoryPolicy(memoryPolicy: MemoryPolicy<Key, Value>): StoreBuilder<Key, Value> {
        this.memoryPolicy = memoryPolicy
        return this
    }


    fun build(): Store<Key, Value> {

        val memoryCache = DefaultMemoryCache<Key, Value>()

        return RealStore(
            fetcher,
            memoryCache,
            sourceOfTruth, scope, memoryPolicy
        )
    }

    companion object {
        fun <Key : Any, Value : Any> from(
            fetcher: suspend (Key) -> Value?
        ): StoreBuilder<Key, Value> = StoreBuilder(fetcher = fetcher)
    }
}