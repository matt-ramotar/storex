package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.StoreKey
import kotlinx.coroutines.flow.Flow

/**
 * Source of Truth (SoT) - Local persistence layer for Store.
 *
 * The SoT is the authoritative local cache for Store data. It provides:
 * - **Durable storage**: Data survives app restarts (database, files, etc.)
 * - **Reactive reads**: Flow-based observation of changes
 * - **Transaction support**: Atomic multi-key operations
 * - **CQRS support**: Separate read and write models
 *
 * ## Architecture
 *
 * ```
 * Memory Cache  →  SoT  →  Network
 *     (fast)      (durable)  (remote)
 * ```
 *
 * ## CQRS Pattern
 *
 * The SoT supports separate read and write types for Command Query Responsibility Segregation:
 * - **ReadDb**: Optimized for queries (denormalized, projections)
 * - **WriteDb**: Optimized for writes (normalized, aggregates, deltas)
 *
 * For simple cases where ReadDb == WriteDb, use the same type for both parameters.
 *
 * @param K The [StoreKey] subtype identifying stored entities
 * @param ReadDb The database read projection type - what queries return
 * @param WriteDb The database write model type - what writes accept
 *
 * @see reader For reactive observation of data changes
 * @see write For persisting data
 * @see delete For removing data
 * @see withTransaction For atomic multi-operation transactions
 * @see rekey For updating keys after server assigns canonical IDs
 *
 * ## Example: Simple SoT (same read/write type)
 * ```kotlin
 * class UserSoT : SourceOfTruth<UserKey, UserEntity, UserEntity> {
 *     override fun reader(key: UserKey): Flow<UserEntity?> =
 *         database.userDao().observeUser(key.id)
 *
 *     override suspend fun write(key: UserKey, value: UserEntity) {
 *         database.userDao().upsert(value)
 *     }
 *
 *     override suspend fun delete(key: UserKey) {
 *         database.userDao().delete(key.id)
 *     }
 *
 *     override suspend fun withTransaction(block: suspend () -> Unit) {
 *         database.withTransaction { block() }
 *     }
 * }
 * ```
 *
 * ## Example: CQRS SoT (separate read/write types)
 * ```kotlin
 * class ArticleSoT : SourceOfTruth<ArticleKey, ArticleReadProjection, ArticleWriteDelta> {
 *     // Reader returns denormalized projection
 *     override fun reader(key: ArticleKey): Flow<ArticleReadProjection?> =
 *         database.articleDao().observeArticleWithAuthor(key.id)
 *
 *     // Writer accepts normalized delta/aggregate
 *     override suspend fun write(key: ArticleKey, value: ArticleWriteDelta) {
 *         database.articleDao().applyDelta(value)
 *     }
 *
 *     override suspend fun delete(key: ArticleKey) {
 *         database.articleDao().delete(key.id)
 *     }
 *
 *     override suspend fun withTransaction(block: suspend () -> Unit) {
 *         database.withTransaction { block() }
 *     }
 * }
 * ```
 *
 * ## Example: Optimistic Offline Support
 * ```kotlin
 * class ProductSoT : SourceOfTruth<ProductKey, ProductEntity, ProductEntity> {
 *     override suspend fun rekey(
 *         old: ProductKey,
 *         new: ProductKey,
 *         reconcile: suspend (oldRead: ProductEntity, serverRead: ProductEntity?) -> ProductEntity
 *     ) {
 *         // After create, server assigns canonical ID
 *         val local = database.productDao().get(old.id) ?: return
 *         val server = database.productDao().get(new.id)
 *         val merged = reconcile(local, server)
 *         database.productDao().delete(old.id)
 *         database.productDao().upsert(merged)
 *     }
 * }
 * ```
 */
interface SourceOfTruth<K : StoreKey, ReadDb, WriteDb> {
    /**
     * Observes data for a specific key.
     *
     * Returns a cold Flow that emits:
     * - `null` if the key doesn't exist
     * - The current value whenever it changes
     *
     * The Flow should:
     * - Emit immediately with current value (hot start)
     * - Emit on every write/delete affecting this key
     * - Never complete (until cancelled)
     *
     * @param key The key to observe
     * @return Flow emitting read projections or null
     */
    fun reader(key: K): Flow<ReadDb?>

    /**
     * Writes data for a specific key.
     *
     * This should:
     * - Upsert (insert or update) the value
     * - Trigger emissions on [reader] flows for this key
     * - Be idempotent (safe to call multiple times with same value)
     *
     * @param key The key to write
     * @param value The write model/delta to persist
     */
    suspend fun write(key: K, value: WriteDb)

    /**
     * Deletes data for a specific key.
     *
     * This should:
     * - Remove the key from persistence
     * - Emit `null` on [reader] flows for this key
     * - Be idempotent (safe to call on non-existent keys)
     *
     * @param key The key to delete
     */
    suspend fun delete(key: K)

    /**
     * Executes a block within a transaction.
     *
     * All writes/deletes in [block] should be atomic:
     * - Either all succeed and commit
     * - Or all fail and rollback
     *
     * Nested transactions should be supported if possible (savepoints).
     *
     * @param block The operations to execute atomically
     */
    suspend fun withTransaction(block: suspend () -> Unit)

    /**
     * Updates a key after server assigns a canonical ID.
     *
     * This is used for optimistic offline creates where:
     * 1. Client uses provisional key (e.g., "temp-123")
     * 2. Server returns canonical key (e.g., "user-456")
     * 3. SoT must migrate data from provisional to canonical key
     *
     * The [reconcile] function handles conflicts if both keys exist:
     * - `oldRead`: The locally created data (from provisional key)
     * - `serverRead`: Existing server data (if canonical key already exists)
     * - Returns: Merged result to write under canonical key
     *
     * Default implementation does nothing. Override to support optimistic creates.
     *
     * @param old The provisional/temporary key used for optimistic create
     * @param new The canonical key assigned by server
     * @param reconcile Function to merge old and new data if both exist
     */
    suspend fun rekey(old: K, new: K, reconcile: suspend (oldRead: ReadDb, serverRead: ReadDb?) -> ReadDb) {
        // Default: no-op. Override to support provisional key migration.
    }

    /**
     * Clears cached flow state for a specific key.
     *
     * This should:
     * - Reset SharedFlow replay cache (if using hot flows)
     * - Clear any in-memory cached state
     * - NOT delete persisted data (use [delete] for that)
     *
     * Used by Store.invalidate() to force a fresh fetch on next read.
     *
     * Default implementation does nothing. Override to support cache invalidation.
     *
     * @param key The key to clear from cache
     */
    suspend fun clearCache(key: K) {
        // Default: no-op. Override to support cache clearing.
    }
}
