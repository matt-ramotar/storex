package dev.mattramotar.storex.store.dsl.internal

import dev.mattramotar.storex.store.Converter
import dev.mattramotar.storex.store.Store
import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.dsl.CacheConfig
import dev.mattramotar.storex.store.dsl.FreshnessConfig
import dev.mattramotar.storex.store.dsl.MutationsConfig
import dev.mattramotar.storex.store.dsl.MutationStoreBuilderScope
import dev.mattramotar.storex.store.dsl.PersistenceConfig
import dev.mattramotar.storex.store.dsl.StoreBuilderScope
import dev.mattramotar.storex.store.internal.Bookkeeper
import dev.mattramotar.storex.store.internal.DefaultDbMeta
import dev.mattramotar.storex.store.internal.DefaultFreshnessValidator
import dev.mattramotar.storex.store.internal.Fetcher
import dev.mattramotar.storex.store.internal.FreshnessValidator
import dev.mattramotar.storex.store.internal.KeyStatus
import dev.mattramotar.storex.store.internal.MemoryCache
import dev.mattramotar.storex.store.internal.MemoryCacheImpl
import dev.mattramotar.storex.store.internal.RealStore
import dev.mattramotar.storex.store.internal.SourceOfTruth
import dev.mattramotar.storex.store.internal.fetcherOf
import dev.mattramotar.storex.store.mutation.Creator
import dev.mattramotar.storex.store.mutation.Deleter
import dev.mattramotar.storex.store.mutation.MutationEncoder
import dev.mattramotar.storex.store.mutation.MutationStore
import dev.mattramotar.storex.store.mutation.Precondition
import dev.mattramotar.storex.store.mutation.Putser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

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
        return RealStore<K, V, V, V, V, Nothing, Nothing, Nothing?, Nothing?, Nothing?>(
            sot = sot as SourceOfTruth<K, V, V>,
            fetcher = actualFetcher as Fetcher<K, V>,
            updater = null,
            creator = null,
            deleter = null,
            putser = null,
            converter = converter as Converter<K, V, V, V, V>,
            encoder = NoOpMutationEncoder(),
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
        return IdentityConverter()
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
 * Default implementation of [MutationStoreBuilderScope].
 */
internal class DefaultMutationStoreBuilderScope<K : StoreKey, V : Any, P, D> :
    MutationStoreBuilderScope<K, V, P, D> {

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
        val updater = createUpdater()
        val creator = createCreator()
        val deleter = createDeleter()
        val putser = createPutser()
        val encoder = createMutationEncoder()

        @Suppress("UNCHECKED_CAST")
        return RealStore<K, V, V, V, V, P, D, Any?, Any?, Any?>(
            sot = sot as SourceOfTruth<K, V, V>,
            fetcher = actualFetcher as Fetcher<K, V>,
            updater = updater as dev.mattramotar.storex.store.internal.Updater<K, P, Any?>?,
            creator = creator as Creator<K, D, V>?,
            deleter = deleter,
            putser = putser as Putser<K, V, Any?>?,
            converter = converter as Converter<K, V, V, V, V>,
            encoder = encoder,
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
        return IdentityConverter()
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

    private fun createUpdater() = mutationsConfig?.updater?.let { updaterFn ->
        SimpleUpdater(updaterFn)
    }

    private fun createCreator() = mutationsConfig?.creator?.let { creatorFn ->
        @Suppress("UNCHECKED_CAST")
        SimpleCreator<K, D, V>(creatorFn as suspend (D) -> dev.mattramotar.storex.store.mutation.CreateOutcome<K, V>)
    }

    private fun createDeleter() = mutationsConfig?.deleter?.let { deleterFn ->
        SimpleDeleter(deleterFn)
    }

    private fun createPutser() = mutationsConfig?.putser?.let { putserFn ->
        SimplePutser(putserFn)
    }

    private fun createMutationEncoder(): MutationEncoder<P, D, V, Any?, Any?, Any?> {
        return object : MutationEncoder<P, D, V, Any?, Any?, Any?> {
            override suspend fun fromPatch(patch: P, base: V?): Any? = patch
            override suspend fun fromDraft(draft: D): Any? = draft
            override suspend fun fromValue(value: V): Any? = value
        }
    }
}

/**
 * Simple identity converter for when ReadDb == WriteDb == V
 */
private class IdentityConverter<K : StoreKey, V : Any> : Converter<K, V, V, V, V> {
    override suspend fun netToDbWrite(key: K, net: V): V = net
    override suspend fun dbReadToDomain(key: K, db: V): V = db
    override suspend fun dbMetaFromProjection(db: V): DefaultDbMeta {
        // No metadata available in simple case
        return DefaultDbMeta(updatedAt = Clock.System.now())
    }

    override suspend fun domainToDbWrite(key: K, value: V): V = value
}

/**
 * In-memory source of truth (no persistence)
 */
private class InMemorySot<K : StoreKey, V : Any> : SourceOfTruth<K, V, V> {
    private val data = mutableMapOf<K, V>()

    override fun reader(key: K): Flow<V?> = kotlinx.coroutines.flow.flow {
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

    override fun reader(key: K): Flow<V?> = kotlinx.coroutines.flow.flow {
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
 * No-op mutation encoder for read-only stores
 */
private class NoOpMutationEncoder<P, D, V> : MutationEncoder<P, D, V, Nothing?, Nothing?, Nothing?> {
    override suspend fun fromPatch(patch: P, base: V?): Nothing? = null
    override suspend fun fromDraft(draft: D): Nothing? = null
    override suspend fun fromValue(value: V): Nothing? = null
}

// Simple adapter classes for mutation operations

private class SimpleUpdater<K : StoreKey, P>(
    private val updateFn: suspend (K, P) -> dev.mattramotar.storex.store.internal.UpdateOutcome<*>
) : dev.mattramotar.storex.store.internal.Updater<K, P, Any?> {
    override suspend fun update(
        key: K,
        patch: P,
        body: Any?,
        precondition: Precondition?
    ): dev.mattramotar.storex.store.internal.Updater.Outcome<*> {
        return when (val outcome = updateFn(key, patch)) {
            is dev.mattramotar.storex.store.internal.UpdateOutcome.Success -> {
                dev.mattramotar.storex.store.internal.Updater.Outcome.Success(outcome.networkEcho, outcome.etag)
            }

            is dev.mattramotar.storex.store.internal.UpdateOutcome.Conflict -> {
                dev.mattramotar.storex.store.internal.Updater.Outcome.Conflict(outcome.serverVersionTag)
            }

            is dev.mattramotar.storex.store.internal.UpdateOutcome.Failure -> {
                dev.mattramotar.storex.store.internal.Updater.Outcome.Failure(outcome.error)
            }
        }
    }
}

private class SimpleCreator<K : StoreKey, D, Net>(
    private val createFn: suspend (D) -> dev.mattramotar.storex.store.mutation.CreateOutcome<K, Net>
) : Creator<K, D, Net> {
    override suspend fun create(draft: D): Creator.Outcome<K, Net> {
        return when (val outcome = createFn(draft)) {
            is dev.mattramotar.storex.store.mutation.CreateOutcome.Success -> {
                @Suppress("UNCHECKED_CAST")
                Creator.Outcome.Success(outcome.canonicalKey, outcome.networkEcho as Net?, outcome.etag)
            }

            is dev.mattramotar.storex.store.mutation.CreateOutcome.Failure -> {
                Creator.Outcome.Failure(outcome.error)
            }
        }
    }
}

private class SimpleDeleter<K : StoreKey>(
    private val deleteFn: suspend (K) -> dev.mattramotar.storex.store.mutation.DeleteOutcome
) : Deleter<K> {
    override suspend fun delete(key: K, precondition: Precondition?): Deleter.Outcome {
        return when (val outcome = deleteFn(key)) {
            is dev.mattramotar.storex.store.mutation.DeleteOutcome.Success -> {
                Deleter.Outcome.Success(outcome.alreadyDeleted, outcome.etag)
            }

            is dev.mattramotar.storex.store.mutation.DeleteOutcome.Failure -> {
                Deleter.Outcome.Failure(outcome.error)
            }
        }
    }
}

private class SimplePutser<K : StoreKey, V : Any>(
    private val putFn: suspend (K, V) -> dev.mattramotar.storex.store.mutation.PutOutcome<K, *>
) : Putser<K, V, Any?> {
    override suspend fun put(
        key: K,
        value: V,
        body: Any?,
        precondition: Precondition?
    ): Putser.Outcome<*> {
        return when (val outcome = putFn(key, value)) {
            is dev.mattramotar.storex.store.mutation.PutOutcome.Created -> {
                Putser.Outcome.Created(outcome.echo, outcome.etag)
            }

            is dev.mattramotar.storex.store.mutation.PutOutcome.Replaced -> {
                Putser.Outcome.Replaced(outcome.echo, outcome.etag)
            }

            is dev.mattramotar.storex.store.mutation.PutOutcome.Failure -> {
                Putser.Outcome.Failure(outcome.error)
            }
        }
    }
}
