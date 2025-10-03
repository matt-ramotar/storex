package dev.mattramotar.storex.store.mutation

import dev.mattramotar.storex.store.Store
import dev.mattramotar.storex.store.StoreKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

interface MutationStore<K : StoreKey, V, Patch, Draft> : Store<K, V> {

    // PATCH/partial update (what write(...) already does)
    suspend fun update(
        key: K,
        patch: Patch,
        policy: UpdatePolicy = UpdatePolicy()
    ): UpdateResult

    // POST/create (you already have this)
    suspend fun create(
        draft: Draft,
        policy: CreatePolicy = CreatePolicy()
    ): CreateResult<K>

    // DELETE with offline-first semantics + tombstones
    suspend fun delete(
        key: K,
        policy: DeletePolicy = DeletePolicy()
    ): DeleteResult

    // PUT create-or-replace (server decides 200 vs 201)
    suspend fun upsert(
        key: K,
        value: V,
        policy: UpsertPolicy = UpsertPolicy()
    ): UpsertResult<K>

    // PUT replace-only (expects resource to exist; else fail or fallback)
    suspend fun replace(
        key: K,
        value: V,
        policy: ReplacePolicy = ReplacePolicy()
    ): ReplaceResult
}


sealed interface Mutation<K : StoreKey, V, Patch, Draft> {
    data class Create<K : StoreKey, V, P, D>(val draft: D, val policy: CreatePolicy) : Mutation<K, V, P, D>
    data class Update<K : StoreKey, V, P, D>(val key: K, val patch: P, val policy: UpdatePolicy) : Mutation<K, V, P, D>
    data class Replace<K : StoreKey, V, P, D>(val key: K, val value: V, val policy: ReplacePolicy) : Mutation<K, V, P, D>
    data class Upsert<K : StoreKey, V, P, D>(val key: K, val value: V, val policy: UpsertPolicy) : Mutation<K, V, P, D>
    data class Delete<K : StoreKey, V, P, D>(val key: K, val policy: DeletePolicy) : Mutation<K, V, P, D>
}


data class CreatePolicy(
    val mode: CreateMode = CreateMode.OfflineFirst,
    val idStrategy: IdStrategy = IdStrategy.ProvisionalUuid,
    val idempotency: Idempotency = Idempotency.Auto, // derive from provisional key
    val conflictStrategy: ConflictStrategy = ConflictStrategy.ServerWins
)

enum class CreateMode { OfflineFirst, OnlineFirst }

sealed interface IdStrategy {
    data object ProvisionalUuid : IdStrategy            // client generates UUID provisional key
    data class ContentHash(val bytesOf: (Any) -> ByteArray) : IdStrategy // for blobs
    data object ServerAllocated : IdStrategy            // no provisional key
}

sealed interface CreateResult<out K> {
    /** Local row exists, awaiting sync (offline-first). */
    data class Local<K>(val provisional: K) : CreateResult<K>

    /** Server accepted; SOT contains canonical row; if different, provisional was rekeyed. */
    data class Synced<K>(val canonical: K, val rekeyedFrom: K?) : CreateResult<K>

    /** Failed now; if OfflineFirst, local provisional may still exist (pending retry). */
    data class Failed<K>(val provisional: K?, val cause: Throwable) : CreateResult<K>
}


data class UpdatePolicy(
    val precondition: Precondition? = null,              // ETag/version guards (If-Match)
    val conflictStrategy: ConflictStrategy = ConflictStrategy.ServerWins,
    val requireOnline: Boolean = false,
    val dedupeWindow: Duration = 150.milliseconds
)

sealed interface UpdateResult {
    data object Enqueued : UpdateResult
    data object Synced : UpdateResult
    data class Failed(val cause: Throwable) : UpdateResult
}

data class DeletePolicy(
    val mode: DeleteMode = DeleteMode.OfflineFirst,
    val precondition: Precondition? = null,              // guard deletes with If-Match, etc.
    val tombstone: TombstonePolicy = TombstonePolicy.Enabled(ttl = 7.days),
    val cascadeQueries: Boolean = true                   // remove from list indexes immediately
)

enum class DeleteMode { OfflineFirst, OnlineFirst }

sealed interface TombstonePolicy {
    data class Enabled(val ttl: Duration) : TombstonePolicy
    data object Disabled : TombstonePolicy
}

sealed interface DeleteResult {
    data object Enqueued : DeleteResult                        // pending remote delete
    data class Synced(val alreadyDeleted: Boolean) : DeleteResult
    data class Failed(val cause: Throwable, val restored: Boolean) : DeleteResult
}

data class UpsertPolicy(
    val mode: UpsertMode = UpsertMode.OfflineFirst,
    val existenceStrategy: ExistenceStrategy = ExistenceStrategy.ServerDecides, // PUT 200/201
    val precondition: Precondition? = null,           // If-None-Match for create-only, If-Match for replace-only
    val idempotency: Idempotency = Idempotency.None   // optional for flaky networks
)

enum class UpsertMode { OfflineFirst, OnlineFirst }
sealed interface ExistenceStrategy {
    data object ServerDecides : ExistenceStrategy     // prefer when server supports PUT upsert
    data object CheckSot : ExistenceStrategy          // conservative; use local presence to choose
    data object CheckRemote : ExistenceStrategy       // HEAD/GET then PATCH/POST (more round-trips)
}

sealed interface UpsertResult<out K> {
    data class Local<K>(val key: K) : UpsertResult<K>
    data class Synced<K>(val key: K, val created: Boolean) : UpsertResult<K>
    data class Failed<K>(val key: K, val cause: Throwable) : UpsertResult<K>
}

data class ReplacePolicy(
    val mode: ReplaceMode = ReplaceMode.OfflineFirst,
    val precondition: Precondition? = null            // often If-Match required
)

enum class ReplaceMode { OfflineFirst, OnlineFirst }

sealed interface ReplaceResult {
    data object Enqueued : ReplaceResult
    data object Synced : ReplaceResult
    data class Failed(val cause: Throwable) : ReplaceResult
}

sealed interface Precondition {
    data class IfMatch(val etag: String) : Precondition
    data class IfNoneMatch(val etag: String = "*") : Precondition // create-only
    data class Version(val value: Long) : Precondition
}

interface Deleter<K : StoreKey> {
    suspend fun delete(key: K, precondition: Precondition? = null): DeleteOutcome
}

sealed interface DeleteOutcome {
    data class Success(val alreadyDeleted: Boolean, val etag: String? = null) : DeleteOutcome
    data class Failure(val error: Throwable, val retryAfter: Duration? = null) : DeleteOutcome
}

interface Putser<K : StoreKey, V, Net> { // Upsert/Replace transport (PUT)
    suspend fun put(
        key: K,
        body: Net,
        precondition: Precondition? = null
    ): PutOutcome<K, Net>
}

sealed interface PutOutcome<out K, out Net> {
    data class Created<K, Net>(val key: K, val echo: Net?, val etag: String?) : PutOutcome<K, Net>
    data class Replaced<K, Net>(val key: K, val echo: Net?, val etag: String?) : PutOutcome<K, Net>
    data class Failure(val error: Throwable, val retryAfter: Duration?) : PutOutcome<Nothing, Nothing>
}

interface Creator<K : StoreKey, D, Net> {
    /**
     * Create a new entity on the server.
     * @param draft: domain draft to create
     * @param provisional: optional client provisional key (for idempotency & echo)
     * @param idempotencyKey: stable key to dedupe server-side (e.g., Stripe-style)
     */
    suspend fun create(
        draft: D,
        provisional: K?,
        idempotencyKey: String?
    ): CreateOutcome<K, Net>
}

sealed interface CreateOutcome<out K, out Net> {
    data class Success<K, Net>(
        val canonicalKey: K,
        val networkEcho: Net? = null,
        val etag: String? = null
    ) : CreateOutcome<K, Net>

    data class Failure(
        val error: Throwable,
        val retryAfter: Duration? = null
    ) : CreateOutcome<Nothing, Nothing>
}

class KeyAliasMap<K : StoreKey> {
    private val aliases = MutableStateFlow<Map<K, K>>(emptyMap())
    fun canonicalOf(k: K): K = aliases.value[k] ?: k
    fun recordAlias(old: K, new: K) {
        aliases.update { it + (old to new) }
    }

    fun flow(): StateFlow<Map<K, K>> = aliases
}

enum class ConflictStrategy {
    ServerWins
}

sealed interface Idempotency {
    data object Auto : Idempotency
    data class Explicit(val value: String) : Idempotency
    data object None : Idempotency

}

interface MutationEncoder<Patch, Draft, V, NetPatch, NetDraft, NetPut> {
    suspend fun fromPatch(patch: Patch, base: V?): NetPatch?
    suspend fun fromDraft(draft: Draft): NetDraft?
    suspend fun fromValue(value: V): NetPut?
}