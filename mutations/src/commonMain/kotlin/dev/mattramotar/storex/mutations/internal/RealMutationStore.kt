package dev.mattramotar.storex.mutations.internal

import dev.mattramotar.storex.core.ByIdKey
import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.EntityId
import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.KeyMutex
import dev.mattramotar.storex.core.Origin
import dev.mattramotar.storex.core.SingleFlight
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreNamespace
import dev.mattramotar.storex.core.StoreResult
import dev.mattramotar.storex.core.internal.Bookkeeper
import dev.mattramotar.storex.core.internal.FetchPlan
import dev.mattramotar.storex.core.internal.FetchRequest
import dev.mattramotar.storex.core.internal.Fetcher
import dev.mattramotar.storex.core.internal.FetcherResult
import dev.mattramotar.storex.core.internal.FreshnessContext
import dev.mattramotar.storex.core.internal.FreshnessValidator
import dev.mattramotar.storex.core.internal.MemoryCache
import dev.mattramotar.storex.core.internal.SourceOfTruth
import dev.mattramotar.storex.mutations.CreatePolicy
import dev.mattramotar.storex.mutations.CreateResult
import dev.mattramotar.storex.mutations.DeleteClient
import dev.mattramotar.storex.mutations.DeletePolicy
import dev.mattramotar.storex.mutations.DeleteResult
import dev.mattramotar.storex.mutations.MutationEncoder
import dev.mattramotar.storex.mutations.MutationStore
import dev.mattramotar.storex.mutations.PatchClient
import dev.mattramotar.storex.mutations.PostClient
import dev.mattramotar.storex.mutations.PutClient
import dev.mattramotar.storex.mutations.ReplacePolicy
import dev.mattramotar.storex.mutations.ReplaceResult
import dev.mattramotar.storex.mutations.UpdatePolicy
import dev.mattramotar.storex.mutations.UpdateResult
import dev.mattramotar.storex.mutations.UpsertPolicy
import dev.mattramotar.storex.mutations.UpsertResult
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
import kotlinx.coroutines.sync.withLock
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
 * val store = RealMutationStore<
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
class RealMutationStore<
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
    private val patchClient: PatchClient<Key, NetworkPatch, NetworkResponse>?,
    private val postClient: PostClient<Key, Draft, NetworkResponse>?,
    private val deleteClient: DeleteClient<Key>?,
    private val putClient: PutClient<Key, NetworkPut, NetworkResponse>?,
    private val converter: Converter<Key, Domain, ReadEntity, NetworkResponse, WriteEntity>,
    private val encoder: MutationEncoder<Patch, Draft, Domain, NetworkPatch, NetworkDraft, NetworkPut>,
    private val bookkeeper: Bookkeeper<Key>,
    private val validator: FreshnessValidator<Key, Any?>,
    private val memory: MemoryCache<Key, Domain>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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
            null
        }
        val dbMeta = initialDb?.let { converter.dbMetaFromProjection(it) }
        val status = bookkeeper.lastStatus(key)
        val plan = validator.plan(FreshnessContext(key, now(), freshness, dbMeta, status))

        suspend fun doFetch() {
            runBlockingFetch(key, plan, errorEvents)
        }

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
            else -> if (plan !is FetchPlan.Skip) launch { doFetch() }
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
            null
        }

        // Optional optimistic local apply
        val optimisticDb: WriteEntity? = try {
            val optimistic = base?.let { maybeApplyLocalPatch(it, patch) }
            optimistic?.let { converter.domainToDbWrite(key, it) }
        } catch (e: Exception) {
            null
        }

        if (optimisticDb != null) {
            sot.withTransaction { sot.write(key, optimisticDb) }
        }

        if (policy.requireOnline && patchClient == null) return UpdateResult.Failed(IllegalStateException("PatchClient not configured"))
        if (patchClient == null) return UpdateResult.Enqueued

        val netPatch = encoder.fromPatch(patch, base) ?: return UpdateResult.Failed(IllegalStateException("NetPatch required"))
        return try {
            when (val response = patchClient.patch(key, payload = netPatch, precondition = policy.precondition)) {
                is PatchClient.Response.Success<*> -> {
                    val echo = response.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (response as? PatchClient.Response.Success<*>)?.etag, now())
                    UpdateResult.Synced
                }
                is PatchClient.Response.Conflict -> {
                    bookkeeper.recordFailure(key, IllegalStateException("Conflict"), now())
                    UpdateResult.Failed(IllegalStateException("Conflict"))
                }
                is PatchClient.Response.Failure -> {
                    bookkeeper.recordFailure(key, response.error, now())
                    UpdateResult.Failed(response.error)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now())
            UpdateResult.Failed(t)
        }
    }

    override suspend fun create(draft: Draft, policy: CreatePolicy): CreateResult<Key> {
        if (policy.requireOnline && postClient == null) return CreateResult.Failed(null, IllegalStateException("PostClient not configured"))
        if (postClient == null) return CreateResult.Failed(null, IllegalStateException("PostClient not configured"))

        return try {
            when (val response = postClient.post(draft)) {
                is PostClient.Response.Success -> {
                    val canonical = response.canonicalKey
                    response.echo?.let { echo ->
                        val writeDb: WriteEntity = converter.netToDbWrite(canonical, echo)
                        sot.withTransaction { sot.write(canonical, writeDb) }
                    }
                    bookkeeper.recordSuccess(canonical, response.etag, now())
                    CreateResult.Synced(canonical, null)
                }
                is PostClient.Response.Failure -> {
                    bookkeeper.recordFailure(fakeKeyForCreate(), response.error, now())
                    CreateResult.Failed(null, response.error)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            CreateResult.Failed(null, t)
        }
    }

    override suspend fun delete(key: Key, policy: DeletePolicy): DeleteResult {
        if (policy.requireOnline && deleteClient == null) return DeleteResult.Failed(cause = IllegalStateException("DeleteClient not configured"), restored = false)
        try {
            sot.withTransaction { sot.delete(key) }
        } catch (e: Exception) {
            // Optimistic delete - ignore failures
        }
        if (deleteClient == null) return DeleteResult.Enqueued
        return try {
            when (val response = deleteClient.delete(key, precondition = null)) {
                is DeleteClient.Response.Success -> { bookkeeper.recordSuccess(key, response.etag, now()); DeleteResult.Synced(alreadyDeleted = response.alreadyDeleted) }
                is DeleteClient.Response.Failure -> { bookkeeper.recordFailure(key, response.error, now()); DeleteResult.Failed(cause = response.error, restored = false) }
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

        if (policy.requireOnline && putClient == null) return UpsertResult.Failed(key = key, cause = IllegalStateException("PutClient not configured"))
        if (putClient == null) return UpsertResult.Local(key)

        val payload = encoder.fromValue(value) ?: return UpsertResult.Failed(key = key, cause = IllegalStateException("NetPut required"))
        return try {
            when (val response = putClient.put(key, payload = payload, precondition = null)) {
                is PutClient.Response.Created<*> -> {
                    val echo = response.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (response as? PutClient.Response.Created<*>)?.etag, now())
                    UpsertResult.Synced(key = key, created = true)
                }
                is PutClient.Response.Replaced<*> -> {
                    val echo = response.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (response as? PutClient.Response.Replaced<*>)?.etag, now())
                    UpsertResult.Synced(key = key, created = false)
                }
                is PutClient.Response.Failure -> {
                    bookkeeper.recordFailure(key, response.error, now())
                    UpsertResult.Failed(key = key, cause = response.error)
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

        if (putClient == null) return ReplaceResult.Enqueued

        val payload = encoder.fromValue(value) ?: return ReplaceResult.Failed(cause = IllegalStateException("NetPut required"))
        return try {
            when (val response = putClient.put(key, payload = payload, precondition = policy.precondition)) {
                is PutClient.Response.Created<*> -> {
                    // For replace, we expect Replaced not Created - this might be an error depending on server behavior
                    val echo = response.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (response as? PutClient.Response.Created<*>)?.etag, now())
                    ReplaceResult.Synced
                }
                is PutClient.Response.Replaced<*> -> {
                    val echo = response.echo
                    if (echo != null) {
                        val writeDb: WriteEntity = converter.netToDbWrite(key, echo as NetworkResponse)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (response as? PutClient.Response.Replaced<*>)?.etag, now())
                    ReplaceResult.Synced
                }
                is PutClient.Response.Failure -> {
                    bookkeeper.recordFailure(key, response.error, now())
                    ReplaceResult.Failed(cause = response.error)
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
