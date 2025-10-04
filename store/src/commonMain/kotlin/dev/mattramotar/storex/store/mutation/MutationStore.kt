package dev.mattramotar.storex.store.mutation

import dev.mattramotar.storex.store.Store
import dev.mattramotar.storex.store.StoreKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Store with full CRUD (Create, Read, Update, Delete) mutation capabilities.
 *
 * Extends [Store] with write operations following REST/HTTP semantics:
 * - **PATCH** ([update]) - Partial updates with optimistic UI support
 * - **POST** ([create]) - Create new entities with provisional keys
 * - **DELETE** ([delete]) - Deletion with tombstones and offline support
 * - **PUT** ([upsert]/[replace]) - Full replacement with create-or-replace semantics
 *
 * ## Offline-First Design
 *
 * All mutations support offline-first operation:
 * 1. **Optimistic update**: Write to local SoT immediately
 * 2. **Background sync**: Queue mutation for network sync
 * 3. **Conflict resolution**: Server wins/client wins/merge strategies
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type
 * @param Patch The type for partial updates (PATCH operations) - can be the same as [Domain] or a dedicated DTO
 * @param Draft The type for creating new entities (POST operations) - can be the same as [Domain] or a creation-specific type
 *
 * @see Store For read-only store operations
 * @see UpdatePolicy For configuring PATCH behavior
 * @see CreatePolicy For configuring POST behavior with provisional IDs
 * @see DeletePolicy For configuring DELETE with tombstones
 *
 * ## Example: User Mutation Store
 * ```kotlin
 * val userStore = mutationStore<UserKey, User, UserPatch, UserDraft> {
 *     fetcher { key -> api.getUser(key.id) }
 *
 *     mutations {
 *         update { key, patch ->
 *             val response = api.updateUser(key.id, patch)
 *             UpdateOutcome.Success(response, response.etag)
 *         }
 *
 *         create { draft ->
 *             val response = api.createUser(draft)
 *             CreateOutcome.Success(UserKey(response.id), response, response.etag)
 *         }
 *
 *         delete { key ->
 *             api.deleteUser(key.id)
 *             DeleteOutcome.Success(alreadyDeleted = false)
 *         }
 *     }
 * }
 *
 * // Usage
 * val result = userStore.update(
 *     key = UserKey("123"),
 *     patch = UserPatch(name = "Alice"),
 *     policy = UpdatePolicy(conflictStrategy = ConflictStrategy.ClientWins)
 * )
 * ```
 */
@JsExport
interface MutationStore<Key : StoreKey, Domain, Patch, Draft> : Store<Key, Domain> {

    /**
     * Partially updates an existing entity (PATCH semantics).
     *
     * Applies a partial update to the entity. Only fields present in [patch] are modified;
     * other fields remain unchanged. Supports optimistic updates and conflict resolution.
     *
     * @param key The entity identifier
     * @param patch The partial update to apply
     * @param policy Controls optimistic updates, preconditions (etag), and conflict resolution
     * @return [UpdateResult] indicating enqueued, synced, or failed
     *
     * ## Example
     * ```kotlin
     * val result = store.update(
     *     key = UserKey("123"),
     *     patch = UserPatch(name = "Bob"),  // Only updates name
     *     policy = UpdatePolicy(
     *         precondition = Precondition.IfMatch("etag-abc"),  // Conditional update
     *         conflictStrategy = ConflictStrategy.ServerWins
     *     )
     * )
     * ```
     */
    @JvmOverloads
    suspend fun update(
        key: Key,
        patch: Patch,
        policy: UpdatePolicy = UpdatePolicy()
    ): UpdateResult

    /**
     * Creates a new entity (POST semantics).
     *
     * Creates a new entity from a draft. Supports:
     * - **Provisional keys**: Client-generated UUID keys for offline creation
     * - **Idempotency**: Automatic deduplication via idempotency keys
     * - **Rekeying**: Server assigns canonical ID, replacing provisional key
     *
     * @param draft The creation payload (e.g., form data without ID)
     * @param policy Controls ID generation, idempotency, and offline behavior
     * @return [CreateResult] with canonical key (may differ from provisional)
     *
     * ## Example
     * ```kotlin
     * val result = store.create(
     *     draft = UserDraft(name = "Alice", email = "alice@example.com"),
     *     policy = CreatePolicy(
     *         idStrategy = IdStrategy.ProvisionalUuid,  // Generate client-side UUID
     *         idempotency = Idempotency.Auto,           // Prevent duplicates on retry
     *         mode = CreateMode.OfflineFirst            // Save locally, sync later
     *     )
     * )
     *
     * when (result) {
     *     is CreateResult.Local -> println("Created locally: ${result.provisional}")
     *     is CreateResult.Synced -> println("Created on server: ${result.canonical}")
     *     is CreateResult.Failed -> println("Failed: ${result.cause}")
     * }
     * ```
     */
    @JvmOverloads
    suspend fun create(
        draft: Draft,
        policy: CreatePolicy = CreatePolicy()
    ): CreateResult<Key>

    /**
     * Deletes an entity (DELETE semantics).
     *
     * Deletes an entity with support for:
     * - **Offline deletion**: Mark deleted locally, sync later
     * - **Tombstones**: Temporary markers to prevent resurrection
     * - **Cascade**: Optionally remove from query indexes
     *
     * @param key The entity identifier
     * @param policy Controls tombstones, cascade, and offline behavior
     * @return [DeleteResult] indicating enqueued, synced, or failed
     *
     * ## Example
     * ```kotlin
     * val result = store.delete(
     *     key = UserKey("123"),
     *     policy = DeletePolicy(
     *         mode = DeleteMode.OfflineFirst,
     *         tombstone = TombstonePolicy.Enabled(ttl = 7.days),  // Keep tombstone for 7 days
     *         cascadeQueries = true  // Remove from list indexes
     *     )
     * )
     * ```
     */
    @JvmOverloads
    suspend fun delete(
        key: Key,
        policy: DeletePolicy = DeletePolicy()
    ): DeleteResult

    /**
     * Creates or replaces an entity (PUT with upsert semantics).
     *
     * If the entity exists, replaces it completely. Otherwise, creates it.
     * Server determines whether creation (201) or replacement (200) occurred.
     *
     * @param key The entity identifier
     * @param value The complete replacement value
     * @param policy Controls existence checking and idempotency
     * @return [UpsertResult] indicating created vs replaced
     *
     * ## Example
     * ```kotlin
     * val result = store.upsert(
     *     key = UserKey("123"),
     *     value = User(id = "123", name = "Alice", email = "alice@example.com"),
     *     policy = UpsertPolicy(
     *         existenceStrategy = ExistenceStrategy.ServerDecides,
     *         idempotency = Idempotency.Explicit("upsert-${timestamp}")
     *     )
     * )
     *
     * when (result) {
     *     is UpsertResult.Synced -> {
     *         if (result.created) println("Created new entity")
     *         else println("Replaced existing entity")
     *     }
     * }
     * ```
     */
    @JvmOverloads
    suspend fun upsert(
        key: Key,
        value: Domain,
        policy: UpsertPolicy = UpsertPolicy()
    ): UpsertResult<Key>

    /**
     * Replaces an existing entity (PUT with replace-only semantics).
     *
     * Replaces an existing entity completely. Fails if entity doesn't exist
     * (unlike [upsert] which would create it).
     *
     * @param key The entity identifier
     * @param value The complete replacement value
     * @param policy Controls preconditions (typically If-Match required)
     * @return [ReplaceResult] indicating enqueued, synced, or failed
     *
     * ## Example
     * ```kotlin
     * val result = store.replace(
     *     key = UserKey("123"),
     *     value = updatedUser,
     *     policy = ReplacePolicy(
     *         precondition = Precondition.IfMatch(currentEtag)  // Optimistic concurrency
     *     )
     * )
     * ```
     */
    @JvmOverloads
    suspend fun replace(
        key: Key,
        value: Domain,
        policy: ReplacePolicy = ReplacePolicy()
    ): ReplaceResult
}


/**
 * Discriminated union of all mutation types.
 *
 * Useful for:
 * - Mutation queues (offline sync)
 * - Replay logs
 * - Undo/redo stacks
 * - Audit trails
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type
 * @param Patch The partial update type
 * @param Draft The creation type
 */
sealed interface Mutation<Key : StoreKey, Domain, Patch, Draft> {
    data class Create<Key : StoreKey, Domain, P, D>(val draft: D, val policy: CreatePolicy) : Mutation<Key, Domain, P, D>
    data class Update<Key : StoreKey, Domain, P, D>(val key: Key, val patch: P, val policy: UpdatePolicy) : Mutation<Key, Domain, P, D>
    data class Replace<Key : StoreKey, Domain, P, D>(val key: Key, val value: Domain, val policy: ReplacePolicy) : Mutation<Key, Domain, P, D>
    data class Upsert<Key : StoreKey, Domain, P, D>(val key: Key, val value: Domain, val policy: UpsertPolicy) : Mutation<Key, Domain, P, D>
    data class Delete<Key : StoreKey, Domain, P, D>(val key: Key, val policy: DeletePolicy) : Mutation<Key, Domain, P, D>
}


@JsExport
data class CreatePolicy(
    val mode: CreateMode = CreateMode.OfflineFirst,
    val idStrategy: IdStrategy = IdStrategy.ProvisionalUuid,
    val idempotency: Idempotency = Idempotency.Auto, // derive from provisional key
    val conflictStrategy: ConflictStrategy = ConflictStrategy.ServerWins,
    val requireOnline: Boolean = false
)

@JsExport
enum class CreateMode { OfflineFirst, OnlineFirst }

@JsExport
sealed interface IdStrategy {
    data object ProvisionalUuid : IdStrategy            // client generates UUID provisional key
    data class ContentHash(val bytesOf: (Any) -> ByteArray) : IdStrategy // for blobs
    data object ServerAllocated : IdStrategy            // no provisional key
}

@JsExport
sealed interface CreateResult<out K> {
    /** Local row exists, awaiting sync (offline-first). */
    data class Local<K>(val provisional: K) : CreateResult<K>

    /** Server accepted; SOT contains canonical row; if different, provisional was rekeyed. */
    data class Synced<K>(val canonical: K, val rekeyedFrom: K?) : CreateResult<K>

    /** Failed now; if OfflineFirst, local provisional may still exist (pending retry). */
    data class Failed<K>(val provisional: K?, val cause: Throwable) : CreateResult<K>
}


@JsExport
data class UpdatePolicy(
    val precondition: Precondition? = null,              // ETag/version guards (If-Match)
    val conflictStrategy: ConflictStrategy = ConflictStrategy.ServerWins,
    val requireOnline: Boolean = false,
    val dedupeWindow: Duration = 150.milliseconds
)

@JsExport
sealed interface UpdateResult {
    data object Enqueued : UpdateResult
    data object Synced : UpdateResult
    data class Failed(val cause: Throwable) : UpdateResult
}

@JsExport
data class DeletePolicy(
    val mode: DeleteMode = DeleteMode.OfflineFirst,
    val precondition: Precondition? = null,              // guard deletes with If-Match, etc.
    val tombstone: TombstonePolicy = TombstonePolicy.Enabled(ttl = 7.days),
    val cascadeQueries: Boolean = true,
    val requireOnline: Boolean = false// remove from list indexes immediately
)

@JsExport
enum class DeleteMode { OfflineFirst, OnlineFirst }

@JsExport
sealed interface TombstonePolicy {
    data class Enabled(val ttl: Duration) : TombstonePolicy
    data object Disabled : TombstonePolicy
}

@JsExport
sealed interface DeleteResult {
    data object Enqueued : DeleteResult                        // pending remote delete
    data class Synced(val alreadyDeleted: Boolean) : DeleteResult
    data class Failed(val cause: Throwable, val restored: Boolean) : DeleteResult
}

@JsExport
data class UpsertPolicy(
    val mode: UpsertMode = UpsertMode.OfflineFirst,
    val existenceStrategy: ExistenceStrategy = ExistenceStrategy.ServerDecides, // PUT 200/201
    val precondition: Precondition? = null,           // If-None-Match for create-only, If-Match for replace-only
    val idempotency: Idempotency = Idempotency.None,   // optional for flaky networks
    val requireOnline: Boolean = false
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

/**
 * Interface for executing delete operations against a remote API.
 *
 * @param Key The [StoreKey] subtype
 */
interface Deleter<Key : StoreKey> {
    sealed interface Outcome {
        data class Success(val alreadyDeleted: Boolean, val etag: String? = null) : Outcome
        data class Failure(val error: Throwable) : Outcome
    }
    suspend fun delete(key: Key, precondition: Precondition?): Outcome
}


sealed interface DeleteOutcome {
    data class Success(val alreadyDeleted: Boolean, val etag: String? = null) : DeleteOutcome
    data class Failure(val error: Throwable, val retryAfter: Duration? = null) : DeleteOutcome
}



/**
 * Interface for executing PUT operations (upsert/replace) against a remote API.
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type
 * @param NetworkPut The network DTO for PUT requests
 */
interface Putser<Key : StoreKey, Domain, NetworkPut> {
    sealed interface Outcome<out Net> {
        data class Created<Net>(val echo: Net? = null, val etag: String? = null) : Outcome<Net>
        data class Replaced<Net>(val echo: Net? = null, val etag: String? = null) : Outcome<Net>
        data class Failure(val error: Throwable) : Outcome<Nothing>
    }
    suspend fun put(key: Key, value: Domain, body: NetworkPut, precondition: Precondition?): Outcome<*>
}

sealed interface PutOutcome<out K, out Net> {
    data class Created<K, Net>(val key: K, val echo: Net?, val etag: String?) : PutOutcome<K, Net>
    data class Replaced<K, Net>(val key: K, val echo: Net?, val etag: String?) : PutOutcome<K, Net>
    data class Failure(val error: Throwable, val retryAfter: Duration?) : PutOutcome<Nothing, Nothing>
}

/**
 * Interface for executing POST/create operations against a remote API.
 *
 * @param Key The [StoreKey] subtype
 * @param Draft The creation payload type
 * @param NetworkResponse The network response type
 */
interface Creator<Key : StoreKey, Draft, NetworkResponse> {
    sealed interface Outcome<out K2 : StoreKey, out Net> {
        data class Success<K2 : StoreKey, Net>(val canonicalKey: K2, val echo: Net? = null, val etag: String? = null) : Outcome<K2, Net>
        data class Failure(val error: Throwable) : Outcome<Nothing, Nothing>
    }
    suspend fun create(draft: Draft): Outcome<Key, NetworkResponse>
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

/**
 * Maps provisional (client-generated) keys to canonical (server-assigned) keys.
 *
 * Used for rekeying after offline creates sync to server.
 *
 * @param Key The [StoreKey] subtype
 *
 * ## Example
 * ```kotlin
 * val aliasMap = KeyAliasMap<UserKey>()
 *
 * // Offline create with provisional UUID
 * val provisionalKey = UserKey("uuid-123")
 *
 * // Server assigns canonical ID
 * val canonicalKey = UserKey("user-456")
 * aliasMap.recordAlias(provisionalKey, canonicalKey)
 *
 * // Lookups resolve to canonical
 * aliasMap.canonicalOf(provisionalKey)  // Returns UserKey("user-456")
 * ```
 */
class KeyAliasMap<Key : StoreKey> {
    private val aliases = MutableStateFlow<Map<Key, Key>>(emptyMap())

    fun canonicalOf(k: Key): Key = aliases.value[k] ?: k

    fun recordAlias(old: Key, new: Key) {
        aliases.update { it + (old to new) }
    }

    fun flow(): StateFlow<Map<Key, Key>> = aliases
}

data class WritePolicy(
    val requireOnline: Boolean = false,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.ServerWins
)

enum class ConflictStrategy { ServerWins, ClientWins, Merge }



/**
 * Idempotency strategy for mutations (creates and upserts).
 *
 * Idempotency ensures that retrying a mutation doesn't create duplicate resources or
 * unintended side effects. This is critical for:
 * - Flaky network connections (automatic retries)
 * - User-initiated retries (e.g., tapping "Create" multiple times)
 * - Background sync after offline usage
 *
 * ## Strategies
 *
 * ### [Auto] - Automatic Idempotency Key Generation (Recommended for Creates)
 *
 * **What it does:**
 * - Automatically generates an idempotency key from the provisional client-side ID
 * - Sends this key to the server (typically in `Idempotency-Key` header)
 * - Server uses the key to detect duplicate requests
 *
 * **When to use:**
 * - Creating new resources with client-generated provisional IDs
 * - [IdStrategy.ProvisionalUuid] or [IdStrategy.ContentHash]
 * - Your API supports idempotency keys (e.g., Stripe-style APIs)
 *
 * **Example:**
 * ```kotlin
 * store.create(
 *     draft = UserDraft(name = "Alice"),
 *     policy = CreatePolicy(
 *         idStrategy = IdStrategy.ProvisionalUuid,
 *         idempotency = Idempotency.Auto  // ← Derives key from UUID
 *     )
 * )
 * ```
 *
 * **Server behavior:**
 * ```
 * POST /users
 * Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
 * { "name": "Alice" }
 *
 * First request → 201 Created, User ID: 123
 * Retry (same key) → 200 OK, User ID: 123 (already exists)
 * ```
 *
 * **Benefits:**
 * - Zero user input required
 * - Deterministic retry behavior
 * - Works offline-first (provisional ID available immediately)
 *
 * ---
 *
 * ### [Explicit] - Custom Idempotency Key (For Business Logic Keys)
 *
 * **What it does:**
 * - Uses a custom application-defined idempotency key
 * - Useful when idempotency is tied to business logic, not resource ID
 *
 * **When to use:**
 * - Idempotency based on user input (e.g., order number)
 * - Composite keys (e.g., "user-123-purchase-456")
 * - Semantic deduplication (e.g., "Only one active session per device")
 *
 * **Example:**
 * ```kotlin
 * store.upsert(
 *     key = ByIdKey(namespace, EntityId("Order", orderId)),
 *     value = order,
 *     policy = UpsertPolicy(
 *         idempotency = Idempotency.Explicit("order-${userId}-${timestamp}")
 *     )
 * )
 * ```
 *
 * **Server behavior:**
 * ```
 * PUT /orders/456
 * Idempotency-Key: order-123-2025-10-04T12:00:00Z
 * { "items": [...], "total": 99.99 }
 *
 * First request → 201 Created
 * Retry (same key) → 200 OK (idempotent)
 * ```
 *
 * **Use cases:**
 * - Financial transactions (prevent duplicate charges)
 * - Reservation systems (prevent double-booking)
 * - Event logging (deduplicate events)
 *
 * ---
 *
 * ### [None] - No Idempotency (Default for Upserts, Use with Caution)
 *
 * **What it does:**
 * - No idempotency key sent to server
 * - Each retry is treated as a new request
 *
 * **When to use:**
 * - Server operation is inherently idempotent (e.g., PUT to replace resource)
 * - You have other deduplication mechanisms (e.g., database unique constraints)
 * - Performance-critical paths where idempotency overhead is unacceptable
 * - APIs that don't support idempotency keys
 *
 * **Example:**
 * ```kotlin
 * store.replace(
 *     key = key,
 *     value = updatedUser,
 *     policy = ReplacePolicy()  // Idempotency not needed for PUT replace
 * )
 * ```
 *
 * **Risks:**
 * - Retry after network error might create duplicates (for creates)
 * - User double-tap might process twice (if not inherently idempotent)
 *
 * **Safe scenarios:**
 * - Replacing a resource by ID (PUT /users/123) - inherently idempotent
 * - Deleting a resource (DELETE /users/123) - idempotent at HTTP level
 * - Updates where last-write-wins is acceptable
 *
 * ## Implementation Notes
 *
 * **Server Requirements:**
 * - Server must support `Idempotency-Key` header (or custom header)
 * - Server must store keys and return cached responses for duplicates
 * - Typical TTL: 24 hours
 *
 * **Client Behavior:**
 * - [Auto]: `Idempotency-Key: <provisionalId>`
 * - [Explicit]: `Idempotency-Key: <customValue>`
 * - [None]: No header sent
 *
 * **Error Handling:**
 * - If server returns 409 Conflict with different result → rekeying needed
 * - If server returns 500 → retry with same idempotency key
 * - If client restarts → provisional ID persists, idempotency preserved
 *
 * ## Best Practices
 *
 * 1. **For creates**: Use [Auto] with [IdStrategy.ProvisionalUuid]
 * 2. **For upserts**: Use [None] if PUT is truly idempotent, otherwise [Explicit]
 * 3. **For financial operations**: Always use [Explicit] with unique transaction ID
 * 4. **For analytics events**: Use [Explicit] with event ID to deduplicate
 * 5. **For bulk operations**: Consider batch-level idempotency keys
 *
 * ## See Also
 * - [CreatePolicy.idempotency]
 * - [UpsertPolicy.idempotency]
 * - [IdStrategy] for provisional ID generation
 * - Stripe API Idempotency: https://stripe.com/docs/api/idempotent_requests
 */
sealed interface Idempotency {
    /**
     * Automatically derive idempotency key from provisional resource ID.
     *
     * Use with [IdStrategy.ProvisionalUuid] or [IdStrategy.ContentHash].
     * Sends `Idempotency-Key: <provisionalId>` to server.
     *
     * **Recommended for:** Creates with client-generated IDs
     */
    data object Auto : Idempotency

    /**
     * Use an explicit custom idempotency key.
     *
     * Sends `Idempotency-Key: <value>` to server.
     *
     * **Recommended for:** Business logic keys, composite keys, semantic deduplication
     *
     * @property value Custom idempotency key (e.g., "order-123-456", "session-device-789")
     */
    data class Explicit(val value: String) : Idempotency

    /**
     * No idempotency key sent.
     *
     * Each retry is treated as a new request. Use only when:
     * - Operation is inherently idempotent (e.g., PUT replace)
     * - Server doesn't support idempotency keys
     * - Other deduplication mechanisms exist
     *
     * **Warning:** May create duplicates on retry for non-idempotent operations
     *
     * **Recommended for:** PUT/DELETE operations, performance-critical paths
     */
    data object None : Idempotency
}

/**
 * Encodes domain types to network DTOs for mutation operations.
 *
 * Separates network encoding for each mutation type (PATCH, POST, PUT) to support
 * APIs with different request shapes per operation.
 *
 * @param Patch The domain patch type
 * @param Draft The domain draft type
 * @param Domain The application's domain model type
 * @param NetworkPatch The network DTO for PATCH requests
 * @param NetworkDraft The network DTO for POST/create requests
 * @param NetworkPut The network DTO for PUT/upsert requests
 *
 * @see SimpleMutationEncoder For simplified 4-parameter version when all network types are the same
 */
interface MutationEncoder<Patch, Draft, Domain, NetworkPatch, NetworkDraft, NetworkPut> {
    suspend fun fromPatch(patch: Patch, base: Domain?): NetworkPatch?
    suspend fun fromDraft(draft: Draft): NetworkDraft?
    suspend fun fromValue(value: Domain): NetworkPut?
}