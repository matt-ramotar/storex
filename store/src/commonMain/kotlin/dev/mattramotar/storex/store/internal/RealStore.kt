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


class RealStore<
    K : StoreKey,
    V: Any,
    ReadDb,
    WriteDb,          // <-- NEW: strongly typed write value
    NetOut: Any,
    Patch,
    Draft,
    NetPatch,
    NetDraft,
    NetPut
    >(
    private val sot: SourceOfTruth<K, ReadDb, WriteDb>,
    private val fetcher: Fetcher<K, NetOut>,
    private val updater: Updater<K, Patch, NetPatch>?,
    private val creator: Creator<K, Draft, NetOut>?,
    private val deleter: Deleter<K>?,
    private val putser: Putser<K, V, NetPut>?,
    private val converter: Converter<K, V, ReadDb, NetOut, WriteDb>,
    private val encoder: MutationEncoder<Patch, Draft, V, NetPatch, NetDraft, NetPut>,
    private val bookkeeper: Bookkeeper<K>,
    private val validator: FreshnessValidator<K, Any?>,
    private val memory: MemoryCache<K, V>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Instant = { Clock.System.now() }
) : MutationStore<K, V, Patch, Draft>, AutoCloseable {

    private val storeScope = scope
    private val fetchSingleFlight = SingleFlight<K, Unit>()
    private val perKeyMutex = KeyMutex<K>()

    override fun stream(key: K, freshness: Freshness): Flow<StoreResult<V>> = channelFlow {
        val errorEvents = Channel<StoreResult.Error>(capacity = Channel.BUFFERED)

        val initialDb: ReadDb? = try { sot.reader(key).firstOrNull() } catch (_: Throwable) { null }
        val dbMeta = initialDb?.let { converter.dbMetaFromProjection(it) }
        val status = bookkeeper.lastStatus(key)
        val plan = validator.plan(FreshnessContext(key, now(), freshness, dbMeta, status))

        suspend fun doFetch(): Unit = runBlockingFetch(key, plan, errorEvents)

        when (freshness) {
            Freshness.MustBeFresh -> {
                try { if (plan !is FetchPlan.Skip) doFetch() }
                catch (t: Throwable) { send(StoreResult.Error(t, servedStale = false)); return@channelFlow }
            }
            else -> if (plan !is FetchPlan.Skip) storeScope.launch { doFetch() }
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

    override suspend fun get(key: K, freshness: Freshness): V {
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

    override suspend fun update(key: K, patch: Patch, policy: UpdatePolicy): UpdateResult {
        val base: V? = try { sot.reader(key).firstOrNull()?.let { converter.dbReadToDomain(key, it) } } catch (_: Throwable) { null }

        // Optional optimistic local apply
        val optimisticDb: WriteDb? = try {
            val optimistic = base?.let { maybeApplyLocalPatch(it, patch) }
            optimistic?.let { converter.domainToDbWrite(key, it) }
        } catch (_: Throwable) { null }

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
                        val writeDb: WriteDb = converter.netToDbWrite(key, echo as NetOut) // keep if your Updater’s echo == NetOut
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
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now())
            UpdateResult.Failed(t)
        }
    }

    override suspend fun create(draft: Draft, policy: CreatePolicy): CreateResult<K> {
        if (policy.requireOnline && creator == null) return CreateResult.Failed(null, IllegalStateException("Creator not configured"))
        if (creator == null) return CreateResult.Failed(null, IllegalStateException("Creator not configured"))

        return try {
            when (val outcome = creator.create(draft)) {
                is Creator.Outcome.Success -> {
                    val canonical = outcome.canonicalKey
                    outcome.echo?.let { echo ->
                        val writeDb: WriteDb = converter.netToDbWrite(canonical, echo)
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
        } catch (t: Throwable) {
            CreateResult.Failed(null, t)
        }
    }

    override suspend fun delete(key: K, policy: DeletePolicy): DeleteResult {
        if (policy.requireOnline && deleter == null) return DeleteResult.Failed(cause = IllegalStateException("Deleter not configured"), restored = false)
        try { sot.withTransaction { sot.delete(key) } } catch (_: Throwable) { /* optimistic */ }
        if (deleter == null) return DeleteResult.Enqueued
        return try {
            when (val out = deleter.delete(key, precondition = null)) {
                is Deleter.Outcome.Success -> { bookkeeper.recordSuccess(key, out.etag, now()); DeleteResult.Synced(alreadyDeleted = out.alreadyDeleted) }
                is Deleter.Outcome.Failure -> { bookkeeper.recordFailure(key, out.error, now()); DeleteResult.Failed(cause = out.error, restored = false) }
            }
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now()); DeleteResult.Failed(cause = t, restored = false)
        }
    }

    override suspend fun upsert(key: K, value: V, policy: UpsertPolicy): UpsertResult<K> {
        val localDb: WriteDb? = converter.domainToDbWrite(key, value)
        if (localDb != null) sot.withTransaction { sot.write(key, localDb) }

        if (policy.requireOnline && putser == null) return UpsertResult.Failed(key = key, cause = IllegalStateException("Putser not configured"))
        if (putser == null) return UpsertResult.Local(key)

        val body = encoder.fromValue(value) ?: return UpsertResult.Failed(key = key, cause = IllegalStateException("NetPut required"))
        return try {
            when (val out = putser.put(key, value, body = body, precondition = null)) {
                is Putser.Outcome.Created<*> -> {
                    val echo = out.echo
                    if (echo != null) {
                        val writeDb: WriteDb = converter.netToDbWrite(key, echo as NetOut)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (out as? Putser.Outcome.Created<*>)?.etag, now())
                    UpsertResult.Synced(key = key, created = true)
                }
                is Putser.Outcome.Replaced<*> -> {
                    val echo = out.echo
                    if (echo != null) {
                        val writeDb: WriteDb = converter.netToDbWrite(key, echo as NetOut)
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
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now()); UpsertResult.Failed(key = key, cause = t)
        }
    }

    override suspend fun replace(key: K, value: V, policy: ReplacePolicy): ReplaceResult {
        val localDb: WriteDb? = converter.domainToDbWrite(key, value)
        if (localDb != null) sot.withTransaction { sot.write(key, localDb) }

        if (putser == null) return ReplaceResult.Enqueued

        val body = encoder.fromValue(value) ?: return ReplaceResult.Failed(cause = IllegalStateException("NetPut required"))
        return try {
            when (val out = putser.put(key, value, body = body, precondition = policy.precondition)) {
                is Putser.Outcome.Created<*> -> {
                    // For replace, we expect Replaced not Created - this might be an error depending on server behavior
                    val echo = out.echo
                    if (echo != null) {
                        val writeDb: WriteDb = converter.netToDbWrite(key, echo as NetOut)
                        sot.withTransaction { sot.write(key, writeDb) }
                    }
                    bookkeeper.recordSuccess(key, (out as? Putser.Outcome.Created<*>)?.etag, now())
                    ReplaceResult.Synced
                }
                is Putser.Outcome.Replaced<*> -> {
                    val echo = out.echo
                    if (echo != null) {
                        val writeDb: WriteDb = converter.netToDbWrite(key, echo as NetOut)
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
        } catch (t: Throwable) {
            bookkeeper.recordFailure(key, t, now()); ReplaceResult.Failed(cause = t)
        }
    }

    override fun invalidate(key: K) { storeScope.launch { memory.remove(key) } }
    override fun invalidateNamespace(ns: StoreNamespace) { storeScope.launch { memory.clear() } }
    override fun invalidateAll() { storeScope.launch { memory.clear() } }
    override fun close() { storeScope.cancel() }

    private suspend fun runBlockingFetch(key: K, plan: FetchPlan, errorEvents: Channel<StoreResult.Error>) {
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
                            val writeDb: WriteDb = converter.netToDbWrite(key, resp.body)
                            val m = perKeyMutex.forKey(key)
                            m.lock()
                            try { sot.write(key, writeDb) }
                            finally { m.unlock() }
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
            } catch (t: Throwable) {
                bookkeeper.recordFailure(key, t, now())
                errorEvents.trySend(StoreResult.Error(t, servedStale = true))
            }
        }.await()
    }

    private fun fakeKeyForCreate(): K {
        @Suppress("UNCHECKED_CAST")
        return ByIdKey(
            namespace = StoreNamespace("create"),
            entity = EntityId(type = "temp", id = Clock.System.now().toString()),
        ) as K
    }

    protected open fun maybeApplyLocalPatch(base: V, patch: Patch): V? = null
}

/* helper to extract updatedAt from arbitrary meta objects */
private fun Any?.extractUpdatedAt(): Instant? =
    try {
        this as Instant
    }catch(_: Throwable) {
        null
    }

