package dev.mattramotar.storex.store.internal

import dev.mattramotar.storex.store.ByIdKey
import dev.mattramotar.storex.store.Converter
import dev.mattramotar.storex.store.EntityId
import dev.mattramotar.storex.store.Freshness
import dev.mattramotar.storex.store.KeyMutex
import dev.mattramotar.storex.store.Origin
import dev.mattramotar.storex.store.SingleFlight
import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.StoreNamespace
import dev.mattramotar.storex.store.StoreResult
import dev.mattramotar.storex.store.mutation.CreatePolicy
import dev.mattramotar.storex.store.mutation.CreateResult
import dev.mattramotar.storex.store.mutation.Creator
import dev.mattramotar.storex.store.mutation.DeletePolicy
import dev.mattramotar.storex.store.mutation.DeleteResult
import dev.mattramotar.storex.store.mutation.Deleter
import dev.mattramotar.storex.store.mutation.MutationEncoder
import dev.mattramotar.storex.store.mutation.MutationStore
import dev.mattramotar.storex.store.mutation.Putser
import dev.mattramotar.storex.store.mutation.ReplacePolicy
import dev.mattramotar.storex.store.mutation.ReplaceResult
import dev.mattramotar.storex.store.mutation.UpdatePolicy
import dev.mattramotar.storex.store.mutation.UpdateResult
import dev.mattramotar.storex.store.mutation.UpsertPolicy
import dev.mattramotar.storex.store.mutation.UpsertResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant


/**
 * Full-featured store implementation with all capabilities.
 *
 * Coordinates between memory cache, Source of Truth (SoT), and remote fetcher to provide:
 * - **Read operations**: Multi-layer caching with freshness control
 * - **Write operations**: Full CRUD with offline-first support
 * - **Reactive updates**: Flow-based observers with automatic invalidation
 *
 * ## Architecture
 *
 * ```
 * ┌─────────┐      ┌─────────┐      ┌─────────┐
 * │ Memory  │ ───> │   SoT   │ ───> │ Fetcher │
 * │  Cache  │ <─── │ (Local) │ <─── │ (Remote)│
 * └─────────┘      └─────────┘      └─────────┘
 *     Fast           Durable          Source
 * ```
 *
 * ## Generic Parameters (CQRS Support)
 *
 * This class supports Command Query Responsibility Segregation (CQRS) with separate
 * read and write models. For simpler use cases, use [SimpleReadStore], [BasicReadStore],
 * or [BasicMutationStore] type aliases.
 *
 * @param Key The [StoreKey] subtype identifying stored entities (e.g., UserKey, ArticleKey)
 * @param Domain The application's domain model type - what your app works with (e.g., User, Article)
 * @param ReadEntity The database read projection type - optimized for queries (e.g., UserReadProjection)
 * @param WriteEntity The database write model type - optimized for persistence (e.g., UserAggregate)
 * @param NetworkResponse The type returned from network fetch operations (e.g., UserJson, UserDto)
 * @param Patch The type for partial updates / PATCH operations (e.g., UserPatch, UpdateUserRequest)
 * @param Draft The type for creating new entities / POST operations (e.g., UserDraft, CreateUserRequest)
 * @param NetworkPatch The network DTO for PATCH requests (can differ from [Patch])
 * @param NetworkDraft The network DTO for POST/create requests (can differ from [Draft])
 * @param NetworkPut The network DTO for PUT/upsert requests (can differ from [Domain])
 *
 * @see SimpleReadStore For simplified read-only store (Domain == ReadEntity == WriteEntity == NetworkResponse)
 * @see BasicReadStore For basic store with persistence layer (Domain vs Entity separation)
 * @see BasicMutationStore For standard CRUD without complex network encoding
 * @see CqrsStore For CQRS with separate read/write models
 *
 * ## Example: Full CQRS Configuration
 * ```kotlin
 * val store = RealStore<
 *     UserKey,                  // Key
 *     User,                     // Domain (what app uses)
 *     UserReadProjection,       // ReadEntity (denormalized queries)
 *     UserAggregate,            // WriteEntity (normalized writes)
 *     UserJson,                 // NetworkResponse (API response)
 *     UserPatch,                // Patch (domain patch type)
 *     UserDraft,                // Draft (domain creation type)
 *     UserPatchDto,             // NetworkPatch (API patch format)
 *     UserDraftDto,             // NetworkDraft (API creation format)
 *     UserDto                   // NetworkPut (API upsert format)
 * >(
 *     sot = userSoT,
 *     fetcher = userFetcher,
 *     converter = userConverter,
 *     // ... other dependencies
 * )
 * ```
 */
class RealStore<
    Key : StoreKey,
    Domain: Any,
    ReadEntity,
    WriteEntity,
    NetworkResponse: Any,
    Patch,
    Draft,
    NetworkPatch,
    NetworkDraft,
    NetworkPut
    >(
    private val sot: SourceOfTruth<Key, ReadEntity, WriteEntity>,
    private val fetcher: Fetcher<Key, NetworkResponse>,
    private val updater: Updater<Key, Patch, NetworkPatch>?,
    private val creator: Creator<Key, Draft, NetworkResponse>?,
    private val deleter: Deleter<Key>?,
    private val putser: Putser<Key, Domain, NetworkPut>?,
    private val converter: Converter<Key, Domain, ReadEntity, NetworkResponse, WriteEntity>,
    private val encoder: MutationEncoder<Patch, Draft, Domain, NetworkPatch, NetworkDraft, NetworkPut>,
    private val bookkeeper: Bookkeeper<Key>,
    private val validator: FreshnessValidator<Key, Any?>,
    private val memory: MemoryCache<Key, Domain>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),  // Changed to Dispatchers.IO for database operations
    private val now: () -> Instant = { Clock.System.now() }
) : MutationStore<Key, Domain, Patch, Draft>, AutoCloseable {

    private val storeScope = scope
    private val fetchSingleFlight = SingleFlight<Key, Unit>()
    private val perKeyMutex = KeyMutex<Key>()

    override fun stream(key: Key, freshness: Freshness): Flow<StoreResult<Domain>> = channelFlow {
        val errorEvents = Channel<StoreResult.Error>(capacity = Channel.BUFFERED)

        val initialDb: ReadEntity? = try {
            sot.reader(key).firstOrNull()
        } catch (e: Exception) {
            // Never catch CancellationException - only catch Exception
            null
        }
        val dbMeta = initialDb?.let { converter.dbMetaFromProjection(it) }
        val status = bookkeeper.lastStatus(key)
        val plan = validator.plan(FreshnessContext(key, now(), freshness, dbMeta, status))

        when (freshness) {
            Freshness.MustBeFresh -> {
                try {
                    if (plan !is FetchPlan.Skip) doFetch()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    send(StoreResult.Error(t, servedStale = false))
                    return@channelFlow
                }
            }
            else -> if (plan !is FetchPlan.Skip) launch { doFetch() }  // Fixed: Launch in channelFlow scope for proper cancellation
        }

        if (initialDb == null) send(StoreResult.Loading(fromCache = false))

        val sotJob = launch {
            sot.reader(key).mapNotNull { it }.collect { dbValue ->
                val domain = converter.dbReadToDomain(key, dbValue)
                val meta = converter.dbMetaFromProjection(dbValue)
                val updatedAt = meta.extractUpdatedAt() ?: Instant.fromEpochMilliseconds(0)
                val age = now() - updatedAt
                memory.put(key, domain)
                send(StoreResult.Data(domain, origin = Origin.SOT, age = age))
            }
        }
        val errJob = launch { for (e in errorEvents) send(e) }
        joinAll(sotJob, errJob)
    }

    override suspend fun get(key: Key, freshness: Freshness): Domain {
        if (freshness == Freshness.CachedOrFetch) memory.get(key)?.let { return it }
        return stream(key, freshness).mapNotNull { res ->
            when (res) {
                is StoreResult.Data -> res.value
                is StoreResult.Error -> if (!res.servedStale) throw res.throwable else null
                else -> null
            }
        }.firstOrNull() ?: error("Store.get($key) finished without data or error")
    }

    /* ---------------- Writes ---------------- */

    override suspend fun update(key: Key, patch: Patch, policy: UpdatePolicy): UpdateResult {
        val base: Domain? = try {
            sot.reader(key).firstOrNull()?.let { converter.dbReadToDomain(key, it) }
        } catch (e: Exception) {
            // Never catch CancellationException - only catch Exception
            null
        }

        // Optional optimistic local apply
        val optimisticDb: WriteEntity? = try {
            val optimistic = base?.let { maybeApplyLocalPatch(it, patch) }
            optimistic?.let { converter.domainToDbWrite(key, it) }
        } catch (e: Exception) {
            // Never catch CancellationException - only catch Exception
            null
        }

        if (optimisticDb != null) {
            sot.withTransaction { sot.write(key, optimisticDb) }
        }

        if (policy.requireOnline && updater == null) return UpdateResult.Failed(IllegalStateException("Updater not configured"))
        if (updater == null) return UpdateResult.Enqueued

        val netPatch = encoder.fromPatch(patch, base) ?: return UpdateResult.Failed(IllegalStateException("NetPatch required"))
        return try {
            when (val outcome = updater.update(key, patch, body = netPatch, precondition = policy.precondition)) {
                is Updater.Outcome.Success<*> -> {
                    val echo = outcome.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (outcome as? Updater.Outcome.Success<*>)?.etag, now())
                    UpdateResult.Synced
                }
                is Updater.Outcome.Conflict -> {
                    bookkeeper.recordFailure(key, IllegalStateException("Conflict"), now())
                    UpdateResult.Failed(IllegalStateException("Conflict"))
                }
                is Updater.Outcome.Failure -> {
                    bookkeeper.recordFailure(key, outcome.error, now())
                    UpdateResult.Failed(outcome.error)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Always rethrow CancellationException
            throw e
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now())
            UpdateResult.Failed(t)
        }
    }

    override suspend fun create(draft: Draft, policy: CreatePolicy): CreateResult<Key> {
        if (policy.requireOnline && creator == null) return CreateResult.Failed(null, IllegalStateException("Creator not configured"))
        if (creator == null) return CreateResult.Failed(null, IllegalStateException("Creator not configured"))

        return try {
            when (val outcome = creator.create(draft)) {
                is Creator.Outcome.Success -> {
                    val canonical = outcome.canonicalKey
                    outcome.echo?.let { echo ->
                        val writeDb: WriteEntity = converter.netToDbWrite(canonical, echo)
                        sot.withTransaction { sot.write(canonical, writeDb) }
                    }
                    bookkeeper.recordSuccess(canonical, outcome.etag, now())
                    CreateResult.Synced(canonical, null)
                }
                is Creator.Outcome.Failure -> {
                    bookkeeper.recordFailure(fakeKeyForCreate(), outcome.error, now())
                    CreateResult.Failed(null, outcome.error)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            CreateResult.Failed(null, t)
        }
    }

    override suspend fun delete(key: Key, policy: DeletePolicy): DeleteResult {
        if (policy.requireOnline && deleter == null) return DeleteResult.Failed(cause = IllegalStateException("Deleter not configured"), restored = false)
        try {
            sot.withTransaction { sot.delete(key) }
        } catch (e: Exception) {
            // Optimistic delete - ignore failures, never catch CancellationException
        }
        if (deleter == null) return DeleteResult.Enqueued
        return try {
            when (val out = deleter.delete(key, precondition = null)) {
                is Deleter.Outcome.Success -> { bookkeeper.recordSuccess(key, out.etag, now()); DeleteResult.Synced(alreadyDeleted = out.alreadyDeleted) }
                is Deleter.Outcome.Failure -> { bookkeeper.recordFailure(key, out.error, now()); DeleteResult.Failed(cause = out.error, restored = false) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now()); DeleteResult.Failed(cause = t, restored = false)
        }
    }

    override suspend fun upsert(key: Key, value: Domain, policy: UpsertPolicy): UpsertResult<Key> {
        val localDb: WriteEntity? = converter.domainToDbWrite(key, value)
        if (localDb != null) sot.withTransaction { sot.write(key, localDb) }

        if (policy.requireOnline && putser == null) return UpsertResult.Failed(key = key, cause = IllegalStateException("Putser not configured"))
        if (putser == null) return UpsertResult.Local(key)

        val body = encoder.fromValue(value) ?: return UpsertResult.Failed(key = key, cause = IllegalStateException("NetPut required"))
        return try {
            when (val out = putser.put(key, value, body = body, precondition = null)) {
                is Putser.Outcome.Created<*> -> {
                    val echo = out.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (out as? Putser.Outcome.Created<*>)?.etag, now())
                    UpsertResult.Synced(key = key, created = true)
                }
                is Putser.Outcome.Replaced<*> -> {
                    val echo = out.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (out as? Putser.Outcome.Replaced<*>)?.etag, now())
                    UpsertResult.Synced(key = key, created = false)
                }
                is Putser.Outcome.Failure -> {
                    bookkeeper.recordFailure(key, out.error, now())
                    UpsertResult.Failed(key = key, cause = out.error)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now()); UpsertResult.Failed(key = key, cause = t)
        }
    }

    override suspend fun replace(key: Key, value: Domain, policy: ReplacePolicy): ReplaceResult {
        val localDb: WriteEntity? = converter.domainToDbWrite(key, value)
        if (localDb != null) sot.withTransaction { sot.write(key, localDb) }

        if (putser == null) return ReplaceResult.Enqueued

        val body = encoder.fromValue(value) ?: return ReplaceResult.Failed(cause = IllegalStateException("NetPut required"))
        return try {
            when (val out = putser.put(key, value, body = body, precondition = policy.precondition)) {
                is Putser.Outcome.Created<*> -> {
                    // For replace, we expect Replaced not Created - this might be an error depending on server behavior
                    val echo = out.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (out as? Putser.Outcome.Created<*>)?.etag, now())
                    ReplaceResult.Synced
                }
                is Putser.Outcome.Replaced<*> -> {
                    val echo = out.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (out as? Putser.Outcome.Replaced<*>)?.etag, now())
                    ReplaceResult.Synced
                }
                is Putser.Outcome.Failure -> {
                    bookkeeper.recordFailure(key, out.error, now())
                    ReplaceResult.Failed(cause = out.error)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now()); ReplaceResult.Failed(cause = t)
        }
    }

    override fun invalidate(key: Key) { storeScope.launch { memory.remove(key) } }
    override fun invalidateNamespace(ns: StoreNamespace) { storeScope.launch { memory.clear() } }
    override fun invalidateAll() { storeScope.launch { memory.clear() } }
    override fun close() { storeScope.cancel() }

    private suspend fun runBlockingFetch(key: Key, plan: FetchPlan, errorEvents: Channel<StoreResult.Error>) {
        fetchSingleFlight.launch(storeScope, key) {
            try {
                val req = when (plan) {
                    is FetchPlan.Conditional -> FetchRequest(conditional = plan.request)
                    FetchPlan.Unconditional -> FetchRequest()
                    FetchPlan.Skip -> return@launch
                }
                fetcher.fetch(key, req).collect { resp ->
                    when (resp) {
                        is FetcherResult.Success -> {
                            val writeDb: WriteEntity = converter.netToDbWrite(key, resp.body)
                            val m = perKeyMutex.forKey(key)
                            m.withLock {
                                sot.write(key, writeDb)
                            }
                            bookkeeper.recordSuccess(key, resp.etag, now())
                        }
                        is FetcherResult.NotModified -> {
                            bookkeeper.recordSuccess(key, resp.etag, now())
                        }
                        is FetcherResult.Error -> {
                            throw resp.error
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Always rethrow CancellationException
                throw e
            } catch (t: Throwable) {
                bookkeeper.recordFailure(key, t, now())
                errorEvents.trySend(StoreResult.Error(t, servedStale = true))
            }
        }.await()
    }

    private fun fakeKeyForCreate(): Key {
        @Suppress("UNCHECKED_CAST")
        return ByIdKey(
            namespace = StoreNamespace("create"),
            entity = EntityId(type = "temp", id = Clock.System.now().toString()),
        ) as Key
    }

    protected open fun maybeApplyLocalPatch(base: Domain, patch: Patch): Domain? = null
}

/* helper to extract updatedAt from arbitrary meta objects */
private fun Any?.extractUpdatedAt(): Instant? = this as? Instant

