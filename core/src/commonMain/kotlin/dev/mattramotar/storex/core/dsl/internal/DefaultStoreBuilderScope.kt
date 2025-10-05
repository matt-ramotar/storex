package dev.mattramotar.storex.core.dsl.internal

import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.IdentityConverter
import dev.mattramotar.storex.core.SimpleConverterAdapter
import dev.mattramotar.storex.core.Store
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.dsl.CacheConfig
import dev.mattramotar.storex.core.dsl.FreshnessConfig
import dev.mattramotar.storex.core.dsl.PersistenceConfig
import dev.mattramotar.storex.core.dsl.StoreBuilderScope
import dev.mattramotar.storex.core.internal.Bookkeeper
import dev.mattramotar.storex.core.internal.DefaultFreshnessValidator
import dev.mattramotar.storex.core.internal.Fetcher
import dev.mattramotar.storex.core.internal.FreshnessValidator
import dev.mattramotar.storex.core.internal.KeyStatus
import dev.mattramotar.storex.core.internal.MemoryCache
import dev.mattramotar.storex.core.internal.MemoryCacheImpl
import dev.mattramotar.storex.core.internal.RealReadStore
import dev.mattramotar.storex.core.internal.SourceOfTruth
import dev.mattramotar.storex.core.internal.fetcherOf
import dev.mattramotar.storex.core.internal.DefaultDbMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Default implementation of [StoreBuilderScope].
 */
internal class DefaultStoreBuilderScope<K : StoreKey, V : Any> : StoreBuilderScope<K, V> {
    override var fetcher: Fetcher<K, *>? = null
    override var scope: CoroutineScope? = null
    override var cacheConfig: CacheConfig? = null
    override var persistenceConfig: PersistenceConfig<K, V>? = null
    override var freshnessConfig: FreshnessConfig? = null

    override fun fetcher(fetch: suspend (K) -> V) {
        fetcher = fetcherOf(fetch)
    }

    override fun cache(block: CacheConfig.() -> Unit) {
        cacheConfig = CacheConfig().apply(block)
    }

    override fun persistence(block: PersistenceConfig<K, V>.() -> Unit) {
        persistenceConfig = PersistenceConfig<K, V>().apply(block)
    }

    override fun freshness(block: FreshnessConfig.() -> Unit) {
        freshnessConfig = FreshnessConfig().apply(block)
    }

    fun build(): Store<K, V> {
        val actualFetcher = requireNotNull(fetcher) { "fetcher is required" }
        val actualScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val cache = createMemoryCache()
        val sot = createSourceOfTruth()
        val converter = createConverter()
        val validator = createFreshnessValidator()
        val bookkeeper = createBookkeeper()

        @Suppress("UNCHECKED_CAST")
        return RealReadStore<K, V, V, V, V>(
            sot = sot as SourceOfTruth<K, V, V>,
            fetcher = actualFetcher as Fetcher<K, V>,
            converter = converter as Converter<K, V, V, V, V>,
            bookkeeper = bookkeeper,
            validator = validator as FreshnessValidator<K, Any?>,
            memory = cache,
            scope = actualScope
        )
    }

    private fun createMemoryCache(): MemoryCache<K, V> {
        val config = cacheConfig ?: CacheConfig()
        return MemoryCacheImpl(
            maxSize = config.maxSize,
            ttl = config.ttl
        )
    }

    private fun createSourceOfTruth(): SourceOfTruth<K, V, V> {
        val persistence = persistenceConfig
        return if (persistence != null && persistence.reader != null && persistence.writer != null) {
            SimpleSot(
                readFn = persistence.reader!!,
                writeFn = persistence.writer!!,
                deleteFn = persistence.deleter,
                transactional = persistence.transactional ?: { block -> block() }
            )
        } else {
            InMemorySot()
        }
    }

    private fun createConverter(): Converter<K, V, V, V, V> {
        return SimpleConverterAdapter(IdentityConverter<K, V>())
    }

    private fun createFreshnessValidator(): FreshnessValidator<K, DefaultDbMeta> {
        val config = freshnessConfig ?: FreshnessConfig()
        return DefaultFreshnessValidator(
            ttl = config.ttl,
            staleIfError = config.staleIfError
        )
    }

    private fun createBookkeeper(): Bookkeeper<K> {
        return InMemoryBookkeeper()
    }
}

/**
 * In-memory source of truth (no persistence)
 */
private class InMemorySot<K : StoreKey, V : Any> : SourceOfTruth<K, V, V> {
    private val data = mutableMapOf<K, V>()

    override fun reader(key: K): Flow<V?> = flow {
        emit(data[key])
    }

    override suspend fun write(key: K, value: V) {
        data[key] = value
    }

    override suspend fun delete(key: K) {
        data.remove(key)
    }

    override suspend fun withTransaction(block: suspend () -> Unit) {
        block()
    }

    override suspend fun rekey(
        old: K,
        new: K,
        reconcile: suspend (oldRead: V, serverRead: V?) -> V
    ) {
        val oldValue = data.remove(old)
        if (oldValue != null) {
            val reconciled = reconcile(oldValue, data[new])
            data[new] = reconciled
        }
    }
}

/**
 * Simple source of truth backed by user-provided functions
 */
private class SimpleSot<K : StoreKey, V : Any>(
    private val readFn: suspend (K) -> V?,
    private val writeFn: suspend (K, V) -> Unit,
    private val deleteFn: (suspend (K) -> Unit)?,
    private val transactional: suspend (suspend () -> Unit) -> Unit
) : SourceOfTruth<K, V, V> {

    override fun reader(key: K): Flow<V?> = flow {
        emit(readFn.invoke(key))
    }

    override suspend fun write(key: K, value: V) {
        writeFn(key, value)
    }

    override suspend fun delete(key: K) {
        deleteFn?.invoke(key)
    }

    override suspend fun withTransaction(block: suspend () -> Unit) {
        transactional(block)
    }

    override suspend fun rekey(
        old: K,
        new: K,
        reconcile: suspend (oldRead: V, serverRead: V?) -> V
    ) {
        // Simple implementation: delete old, write new
        val oldValue = readFn(old)
        deleteFn?.invoke(old)
        if (oldValue != null) {
            val reconciled = reconcile(oldValue, readFn(new))
            writeFn(new, reconciled)
        }
    }
}

/**
 * In-memory bookkeeper
 */
private class InMemoryBookkeeper<K : StoreKey> : Bookkeeper<K> {
    private val status = mutableMapOf<K, KeyStatus>()

    override fun recordSuccess(key: K, etag: String?, at: Instant) {
        val current = status[key] ?: KeyStatus(null, null, null, null)
        status[key] = current.copy(lastSuccessAt = at, lastEtag = etag)
    }

    override fun recordFailure(key: K, error: Throwable, at: Instant) {
        val current = status[key] ?: KeyStatus(null, null, null, null)
        status[key] = current.copy(lastFailureAt = at)
    }

    override fun lastStatus(key: K): KeyStatus {
        return status[key] ?: KeyStatus(null, null, null, null)
    }
}

private fun KeyStatus.copy(
    lastSuccessAt: Instant? = this.lastSuccessAt,
    lastFailureAt: Instant? = this.lastFailureAt,
    lastEtag: String? = this.lastEtag,
    backoffUntil: Instant? = this.backoffUntil
) = KeyStatus(lastSuccessAt, lastFailureAt, lastEtag, backoffUntil)
