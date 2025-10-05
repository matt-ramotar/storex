package dev.mattramotar.storex.mutations.dsl.internal

import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.IdentityConverter
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.dsl.CacheConfig
import dev.mattramotar.storex.core.dsl.FreshnessConfig
import dev.mattramotar.storex.core.dsl.PersistenceConfig
import dev.mattramotar.storex.core.internal.Bookkeeper
import dev.mattramotar.storex.core.internal.DefaultDbMeta
import dev.mattramotar.storex.core.internal.DefaultFreshnessValidator
import dev.mattramotar.storex.core.internal.Fetcher
import dev.mattramotar.storex.core.internal.FreshnessValidator
import dev.mattramotar.storex.core.internal.KeyStatus
import dev.mattramotar.storex.core.internal.MemoryCache
import dev.mattramotar.storex.core.internal.SourceOfTruth
import dev.mattramotar.storex.core.internal.fetcherOf
import dev.mattramotar.storex.mutations.DeleteClient
import dev.mattramotar.storex.mutations.MutationEncoder
import dev.mattramotar.storex.mutations.MutationStore
import dev.mattramotar.storex.mutations.PatchClient
import dev.mattramotar.storex.mutations.PostClient
import dev.mattramotar.storex.mutations.Precondition
import dev.mattramotar.storex.mutations.PutClient
import dev.mattramotar.storex.mutations.SimpleMutationEncoder
import dev.mattramotar.storex.mutations.SimpleMutationEncoderAdapter
import dev.mattramotar.storex.mutations.dsl.MutationStoreBuilderScope
import dev.mattramotar.storex.mutations.dsl.MutationsConfig
import dev.mattramotar.storex.mutations.internal.RealMutationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Default implementation of [MutationStoreBuilderScope].
 */
internal class DefaultMutationStoreBuilderScope<K : StoreKey, V : Any, P, D> : MutationStoreBuilderScope<K, V, P, D> {
    override var fetcher: Fetcher<K, *>? = null
    override var scope: CoroutineScope? = null
    override var cacheConfig: CacheConfig? = null
    override var persistenceConfig: PersistenceConfig<K, V>? = null
    override var freshnessConfig: FreshnessConfig? = null
    override var mutationsConfig: MutationsConfig<K, V, P, D>? = null

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

    override fun mutations(block: MutationsConfig<K, V, P, D>.() -> Unit) {
        mutationsConfig = MutationsConfig<K, V, P, D>().apply(block)
    }

    fun build(): MutationStore<K, V, P, D> {
        val actualFetcher = requireNotNull(fetcher) { "fetcher is required" }
        val actualScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val cache = createMemoryCache()
        val sot = createSourceOfTruth()
        val converter = createConverter()
        val validator = createFreshnessValidator()
        val bookkeeper = createBookkeeper()
        val simpleEncoder = createEncoder()
        val encoder = SimpleMutationEncoderAdapter(simpleEncoder)

        val patchClient = createPatchClient()
        val postClient = createPostClient()
        val deleteClient = createDeleteClient()
        val putClient = createPutClient()

        @Suppress("UNCHECKED_CAST")
        return RealMutationStore<K, V, V, V, V, P, D, V, V, V>(
            sot = sot as SourceOfTruth<K, V, V>,
            fetcher = actualFetcher as Fetcher<K, V>,
            patchClient = patchClient as PatchClient<K, V, V>?,
            postClient = postClient as PostClient<K, D, V>?,
            deleteClient = deleteClient,
            putClient = putClient as PutClient<K, V, V>?,
            converter = converter as Converter<K, V, V, V, V>,
            encoder = encoder as MutationEncoder<P, D, V, V, V, V>,
            bookkeeper = bookkeeper,
            validator = validator as FreshnessValidator<K, Any?>,
            memory = cache,
            scope = actualScope
        )
    }

    private fun createMemoryCache(): MemoryCache<K, V> {
        val config = cacheConfig ?: CacheConfig()
        return InMemoryCache(
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
        val identityConverter = IdentityConverter<K, V>()
        return object : Converter<K, V, V, V, V> {
            override suspend fun netToDbWrite(key: K, net: V): V = identityConverter.fromNetwork(key, net)
            override suspend fun dbReadToDomain(key: K, db: V): V = identityConverter.toDomain(key, db)
            override suspend fun dbMetaFromProjection(db: V): Any? = identityConverter.extractMetadata(db)
            override suspend fun netMeta(net: V): Converter.NetMeta = identityConverter.extractNetworkMetadata(net)
            override suspend fun domainToDbWrite(key: K, value: V): V? = identityConverter.fromDomain(key, value)
        }
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

    private fun createEncoder(): SimpleMutationEncoder<P, D, V, V> {
        return object : SimpleMutationEncoder<P, D, V, V> {
            @Suppress("UNCHECKED_CAST")
            override suspend fun encodePatch(patch: P, base: V?): V? = patch as? V
            @Suppress("UNCHECKED_CAST")
            override suspend fun encodeDraft(draft: D): V? = draft as? V
            override suspend fun encodeValue(value: V): V = value
        }
    }

    private fun createPatchClient(): PatchClient<K, V, V>? {
        val mutations = mutationsConfig ?: return null
        val patchFn = mutations.patch ?: return null

        return object : PatchClient<K, V, V> {
            override suspend fun patch(key: K, payload: V, precondition: Precondition?): PatchClient.Response<V> {
                @Suppress("UNCHECKED_CAST")
                return patchFn(key, payload as P) as PatchClient.Response<V>
            }
        }
    }

    private fun createPostClient(): PostClient<K, D, V>? {
        val mutations = mutationsConfig ?: return null
        val postFn = mutations.post ?: return null

        return object : PostClient<K, D, V> {
            override suspend fun post(draft: D): PostClient.Response<K, V> {
                @Suppress("UNCHECKED_CAST")
                return postFn(draft) as PostClient.Response<K, V>
            }
        }
    }

    private fun createDeleteClient(): DeleteClient<K>? {
        val mutations = mutationsConfig ?: return null
        val deleteFn = mutations.delete ?: return null

        return object : DeleteClient<K> {
            override suspend fun delete(key: K, precondition: Precondition?): DeleteClient.Response {
                return deleteFn(key)
            }
        }
    }

    private fun createPutClient(): PutClient<K, V, V>? {
        val mutations = mutationsConfig ?: return null
        val putFn = mutations.put ?: mutations.replace ?: return null

        return object : PutClient<K, V, V> {
            override suspend fun put(key: K, payload: V, precondition: Precondition?): PutClient.Response<V> {
                @Suppress("UNCHECKED_CAST")
                return putFn(key, payload) as PutClient.Response<V>
            }
        }
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

/**
 * In-memory cache implementation
 */
private class InMemoryCache<K : Any, V : Any>(
    private val maxSize: Int = 100,
    private val ttl: Duration = 5.minutes
) : MemoryCache<K, V> {
    private val cache = mutableMapOf<K, CacheEntry<V>>()

    data class CacheEntry<V>(val value: V, val timestamp: Instant)

    override suspend fun get(key: K): V? {
        val entry = cache[key] ?: return null
        val now = Clock.System.now()
        return if (now - entry.timestamp < ttl) {
            entry.value
        } else {
            cache.remove(key)
            null
        }
    }

    override suspend fun put(key: K, value: V): Boolean {
        val isNew = key !in cache
        if (cache.size >= maxSize && isNew) {
            // Simple LRU: remove oldest entry
            val oldest = cache.entries.minByOrNull { it.value.timestamp }
            oldest?.let { cache.remove(it.key) }
        }
        cache[key] = CacheEntry(value, Clock.System.now())
        return isNew
    }

    override suspend fun remove(key: K): Boolean {
        return cache.remove(key) != null
    }

    override suspend fun clear() {
        cache.clear()
    }
}
