package dev.mattramotar.storex.store

import dev.mattramotar.storex.store.internal.RealStore
import dev.mattramotar.storex.store.mutation.MutationStore

/**
 * Type aliases to reduce generic parameter explosion in common cases.
 *
 * ## Problem
 *
 * The full [RealStore] implementation has 10 generic parameters:
 * ```
 * RealStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut>
 * ```
 *
 * This creates several issues:
 * - Complex type signatures in error messages
 * - Type inference failures
 * - Difficult to read and maintain
 * - Intimidating for new users
 *
 * ## Solution
 *
 * These type aliases cover common patterns:
 *
 * ### 1. SimpleStore - When all types are the same
 *
 * Use when:
 * - Your domain, network, and database types are identical
 * - No transformation needed
 * - In-memory only, or simple JSON storage
 *
 * ```kotlin
 * // Instead of:
 * RealStore<UserKey, User, User, User, User, Nothing, Nothing, Nothing?, Nothing?, Nothing?>
 *
 * // Use:
 * SimpleReadStore<UserKey, User>
 * ```
 *
 * ### 2. BasicStore - Separate domain and persistence
 *
 * Use when:
 * - Domain type differs from database/network type
 * - Single conversion layer (Entity type used for both read and write)
 * - Most common pattern
 *
 * ```kotlin
 * // Instead of:
 * RealStore<UserKey, User, UserEntity, UserEntity, UserEntity, Nothing, Nothing, Nothing?, Nothing?, Nothing?>
 *
 * // Use:
 * BasicReadStore<UserKey, User, UserEntity>
 * ```
 *
 * ### 3. MutatingStore - Basic mutations without complex encoding
 *
 * Use when:
 * - You need create/update/delete operations
 * - Patches and drafts don't need separate network encoding
 * - Standard CRUD API
 *
 * ```kotlin
 * // Instead of:
 * RealStore<UserKey, User, UserEntity, UserEntity, UserEntity, UserPatch, UserDraft, Any?, Any?, Any?>
 *
 * // Use:
 * BasicMutationStore<UserKey, User, UserEntity, UserPatch, UserDraft>
 * ```
 *
 * ## Migration Path
 *
 * Existing code using RealStore continues to work unchanged. New code should prefer:
 * 1. Start with the simplest alias (SimpleReadStore)
 * 2. Move to BasicReadStore if you need separate Entity type
 * 3. Move to BasicMutationStore if you need mutations
 * 4. Fall back to full RealStore only for complex CQRS scenarios
 */

/**
 * Read-only store where domain == database == network.
 *
 * Simplest possible store configuration. Use for:
 * - In-memory caching
 * - Simple JSON APIs where response matches your domain model
 * - Prototyping
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type (used for all layers)
 *
 * ## Example
 * ```kotlin
 * val userStore: SimpleReadStore<UserKey, User> = store {
 *     fetcher { key -> api.getUser(key.id) }
 * }
 * ```
 */
typealias SimpleReadStore<Key, Domain> = RealStore<
    Key,        // Key
    Domain,     // Domain value
    Domain,     // ReadEntity = Domain
    Domain,     // WriteEntity = Domain
    Domain,     // NetworkResponse = Domain
    Nothing,    // No Patch
    Nothing,    // No Draft
    Nothing?,   // No NetworkPatch
    Nothing?,   // No NetworkDraft
    Nothing?    // No NetworkPut
>

/**
 * Read-only store with separate database/persistence type.
 *
 * Use when:
 * - Database schema differs from domain model
 * - Network JSON differs from domain model
 * - You need a conversion layer
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type
 * @param Entity The unified database/network type
 *
 * ## Example
 * ```kotlin
 * val articleStore: BasicReadStore<ArticleKey, Article, ArticleEntity> = store {
 *     fetcher { key -> api.getArticle(key.id) }
 *     converter = articleConverter  // Converts ArticleEntity ↔ Article
 *     persistence {
 *         reader { key -> database.getArticle(key.id) }
 *         writer { key, entity -> database.save(entity) }
 *     }
 * }
 * ```
 */
typealias BasicReadStore<Key, Domain, Entity> = RealStore<
    Key,        // Key
    Domain,     // Domain value
    Entity,     // ReadEntity
    Entity,     // WriteEntity = ReadEntity
    Entity,     // NetworkResponse = Entity
    Nothing,    // No Patch
    Nothing,    // No Draft
    Nothing?,   // No NetworkPatch
    Nothing?,   // No NetworkDraft
    Nothing?    // No NetworkPut
>

/**
 * Store with basic mutations (create/update/delete).
 *
 * Use when:
 * - You need CRUD operations
 * - Patches and drafts are domain types (or easily convertible)
 * - Standard REST API
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type
 * @param Entity The unified database/network type
 * @param Patch The type for partial updates (PATCH operations)
 * @param Draft The type for resource creation (POST operations)
 *
 * ## Example
 * ```kotlin
 * val userStore: BasicMutationStore<UserKey, User, UserEntity, UserPatch, UserDraft> =
 *     mutationStore {
 *         fetcher { key -> api.getUser(key.id) }
 *         converter = userConverter
 *
 *         mutations {
 *             update { key, patch -> api.updateUser(key.id, patch) }
 *             create { draft -> api.createUser(draft) }
 *             delete { key -> api.deleteUser(key.id) }
 *         }
 *     }
 * ```
 */
typealias BasicMutationStore<Key, Domain, Entity, Patch, Draft> = RealStore<
    Key,        // Key
    Domain,     // Domain value
    Entity,     // ReadEntity
    Entity,     // WriteEntity = ReadEntity
    Entity,     // NetworkResponse = Entity
    Patch,      // Patch type
    Draft,      // Draft type
    Any?,       // NetworkPatch = Any (encoder handles it)
    Any?,       // NetworkDraft = Any (encoder handles it)
    Any?        // NetworkPut = Any (encoder handles it)
>

/**
 * Full mutation store with separate network encoding for mutations.
 *
 * Use when:
 * - Your API requires different DTOs for different operations
 * - PATCH uses different shape than POST
 * - PUT uses different shape than PATCH/POST
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type
 * @param Entity The unified database/network type
 * @param Patch The type for partial updates (PATCH operations)
 * @param Draft The type for resource creation (POST operations)
 * @param NetworkPatch The network DTO for PATCH requests
 * @param NetworkDraft The network DTO for POST/create requests
 * @param NetworkPut The network DTO for PUT/upsert requests
 *
 * ## Example
 * ```kotlin
 * val complexStore: AdvancedMutationStore<
 *     OrderKey,
 *     Order,
 *     OrderEntity,
 *     OrderPatch,
 *     OrderDraft,
 *     OrderPatchDto,
 *     OrderDraftDto,
 *     OrderPutDto
 * > = mutationStore {
 *     // ... complex configuration
 * }
 * ```
 */
typealias AdvancedMutationStore<Key, Domain, Entity, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut> = RealStore<
    Key,            // Key
    Domain,         // Domain value
    Entity,         // ReadEntity
    Entity,         // WriteEntity = ReadEntity
    Entity,         // NetworkResponse = Entity
    Patch,          // Patch type
    Draft,          // Draft type
    NetworkPatch,   // Network patch encoding
    NetworkDraft,   // Network draft encoding
    NetworkPut      // Network put encoding
>

/**
 * CQRS store with separate read and write database types.
 *
 * Use when:
 * - Read model (projections) differs from write model (aggregates)
 * - Event sourcing architecture
 * - Denormalized reads, normalized writes
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type
 * @param ReadEntity The database read projection type (query results from SoT)
 * @param WriteEntity The database write model type (what gets persisted to SoT)
 * @param Patch The type for partial updates (PATCH operations)
 * @param Draft The type for resource creation (POST operations)
 *
 * ## Example
 * ```kotlin
 * val cqrsStore: CqrsStore<
 *     UserKey,
 *     UserView,           // Read: denormalized view
 *     UserProjection,     // ReadEntity: query projection
 *     UserAggregate,      // WriteEntity: normalized aggregate
 *     UserCommand,        // Patches are commands
 *     CreateUser          // Drafts are commands
 * > = mutationStore {
 *     // ... CQRS configuration
 * }
 * ```
 */
typealias CqrsStore<Key, Domain, ReadEntity, WriteEntity, Patch, Draft> = RealStore<
    Key,            // Key
    Domain,         // Domain value
    ReadEntity,     // Read database type
    WriteEntity,    // Write database type (different!)
    ReadEntity,     // Network returns read format
    Patch,          // Commands for updates
    Draft,          // Commands for creation
    Any?,           // NetworkPatch encoded from commands
    Any?,           // NetworkDraft encoded from commands
    Any?            // NetworkPut encoded from value
>

/**
 * Helper function to cast a Store to its underlying RealStore implementation.
 *
 * Useful when you need access to internal RealStore features or
 * need to work around type inference issues.
 *
 * **Warning:** This is an escape hatch. Prefer using the Store interface
 * when possible.
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type
 * @param ReadEntity The database read projection type
 * @param WriteEntity The database write model type
 * @param NetworkResponse The type returned from network fetch operations
 * @param Patch The type for partial updates
 * @param Draft The type for resource creation
 * @param NetworkPatch The network DTO for PATCH requests
 * @param NetworkDraft The network DTO for POST/create requests
 * @param NetworkPut The network DTO for PUT/upsert requests
 */
@Suppress("UNCHECKED_CAST")
fun <Key : StoreKey, Domain, ReadEntity, WriteEntity, NetworkResponse : Any, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut>
    Store<Key, Domain>.asRealStore(): RealStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut> {
    return this as RealStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut>
}

/**
 * Helper function to cast a MutationStore to its underlying RealStore implementation.
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type
 * @param Patch The type for partial updates
 * @param Draft The type for resource creation
 * @param ReadEntity The database read projection type
 * @param WriteEntity The database write model type
 * @param NetworkResponse The type returned from network fetch operations
 * @param NetworkPatch The network DTO for PATCH requests
 * @param NetworkDraft The network DTO for POST/create requests
 * @param NetworkPut The network DTO for PUT/upsert requests
 */
@Suppress("UNCHECKED_CAST")
fun <Key : StoreKey, Domain, Patch, Draft, ReadEntity, WriteEntity, NetworkResponse : Any, NetworkPatch, NetworkDraft, NetworkPut>
    MutationStore<Key, Domain, Patch, Draft>.asRealStore(): RealStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut> {
    return this as RealStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut>
}
