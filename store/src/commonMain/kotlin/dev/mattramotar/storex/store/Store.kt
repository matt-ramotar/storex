package dev.mattramotar.storex.store

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
    suspend fun netToDbWrite(key: K, net: NetOut): WriteDb         // NormDelta
    suspend fun dbReadToDomain(key: K, db: ReadDb): V              // Projection -> V
    suspend fun dbMetaFromProjection(db: ReadDb): Any?             // for FreshnessValidator
    suspend fun netMeta(net: NetOut): NetMeta = NetMeta()
}

data class NetMeta(
    val etag: String? = null,
    val lastModified: Instant? = null
)