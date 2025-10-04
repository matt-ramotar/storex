package dev.mattramotar.storex.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline
import kotlin.time.Duration

@JvmInline
value class StoreNamespace(val value: String)

sealed interface StoreKey {
    val namespace: StoreNamespace
    fun stableHash(): Long
}

data class EntityId(val type: String, val id: String)

data class ByIdKey(
    override val namespace: StoreNamespace,
    val entity: EntityId,
) : StoreKey {
    override fun stableHash(): Long =
        (namespace.value + ":" + entity.type + ":" + entity.id).hashCode().toLong()
}


data class QueryKey(
    override val namespace: StoreNamespace,
    val query: Map<String, String>,
) : StoreKey {
    override fun stableHash(): Long =
        (namespace.value + ":" + query.toList().sortedBy { it.first })
            .hashCode().toLong()
}

sealed interface Freshness {
    data object CachedOrFetch : Freshness             // serve cached, trigger refresh
    data class MinAge(val notOlderThan: Duration) : Freshness  // must be at least this fresh
    data object MustBeFresh : Freshness               // force fetch; fail if remote fails (configurable)
    data object StaleIfError : Freshness              // prefer cached if fetch errors
}

interface Store<K : StoreKey, V> {

    suspend fun get(
        key: K,
        freshness: Freshness = Freshness.CachedOrFetch
    ): V // suspends until first emission or throws

    fun stream(
        key: K,
        freshness: Freshness = Freshness.CachedOrFetch
    ): kotlinx.coroutines.flow.Flow<StoreResult<V>>

    fun invalidate(key: K)        // evict caches & signal observers
    fun invalidateNamespace(ns: StoreNamespace)
    fun invalidateAll()
}

sealed interface StoreResult<out V> {
    data class Data<V>(val value: V, val origin: Origin, val age: Duration) : StoreResult<V>
    data class Loading(val fromCache: Boolean) : StoreResult<Nothing>
    data class Error(val throwable: Throwable, val servedStale: Boolean) : StoreResult<Nothing>
}

enum class Origin { MEMORY, SOT, FETCHER }

fun interface StoreInterceptor<K: StoreKey, V> {
    suspend fun intercept(
        chain: Chain<K, V>,
        key: K,
        proceed: suspend () -> StoreResult<V>
    ): StoreResult<V>

    interface Chain<K : StoreKey, V> {
        val context: CoroutineContext
        suspend fun proceed(): StoreResult<V>
    }
}


interface Converter<K : StoreKey, V, ReadDb, NetOut, WriteDb> {
    suspend fun netToDbWrite(key: K, net: NetOut): WriteDb
    suspend fun dbReadToDomain(key: K, db: ReadDb): V
    suspend fun dbMetaFromProjection(db: ReadDb): Any?
    suspend fun netMeta(net: NetOut): NetMeta = NetMeta()

    data class NetMeta(
        val etag: String? = null,
        val lastModifiedMillis: Long? = null
    )

    /** Optional: allows optimistic local writes if you can derive WriteDb from domain */
    suspend fun domainToDbWrite(key: K, value: V): WriteDb? = null
}


/**
 * Single-flight per key: coalesce concurrent requests to a single job.
 */
internal class SingleFlight<K, R> {
    private val inFlight = hashMapOf<K, CompletableDeferred<R>>()

    fun launch(scope: CoroutineScope, key: K, block: suspend () -> R): CompletableDeferred<R> {
        val existing = inFlight[key]
        if (existing != null) return existing

        val deferred = CompletableDeferred<R>()
        val prev = inFlight.put(key, deferred)
        if (prev != null) return prev

        scope.launch {
            try {
                val result = block()
                deferred.complete(result)
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            } finally {
                inFlight.remove(key)
            }
        }
        return deferred
    }
}

internal class KeyMutex<K> {
    private val map = hashMapOf<K, Mutex>()
    fun forKey(key: K): Mutex = map.getOrPut(key) { Mutex() }
}