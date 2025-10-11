package dev.mattramotar.storex.mutations

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.mutations.internal.RealMutationStore

/**
 * Type aliases to reduce generic parameter explosion in common mutation store cases.
 *
 * ## Problem
 *
 * The full [RealMutationStore] implementation has 10 generic parameters:
 * ```
 * RealMutationStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut>
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
 * ### 1. SimpleMutationStore - When all types are the same
 *
 * Use when:
 * - Your domain, network, and database types are identical
 * - Patch, Draft, and Domain are the same
 * - No transformation needed
 * - In-memory only, or simple JSON storage
 *
 * ```kotlin
 * // Instead of:
 * RealMutationStore<UserKey, User, User, User, User, User, User, User, User, User>
 *
 * // Use:
 * SimpleMutationStore<UserKey, User>
 * ```
 *
 * ### 2. BasicMutationStore - Separate domain, patch, and draft
 *
 * Use when:
 * - Domain type differs from patch/draft types
 * - Single entity type used for database and network
 * - Most common pattern
 *
 * ```kotlin
 * // Instead of:
 * RealMutationStore<UserKey, User, UserEntity, UserEntity, UserEntity, UserPatch, UserDraft, UserEntity, UserEntity, UserEntity>
 *
 * // Use:
 * BasicMutationStore<UserKey, User, UserPatch, UserDraft, UserEntity>
 * ```
 *
 * ### 3. CqrsMutationStore - CQRS with separate read/write models
 *
 * Use when:
 * - Read model (projections) differs from write model (aggregates)
 * - Event sourcing architecture
 * - Denormalized reads, normalized writes
 *
 * ```kotlin
 * // Instead of:
 * RealMutationStore<UserKey, User, UserProjection, UserAggregate, UserDto, UserPatch, UserDraft, UserPatchDto, UserDraftDto, UserDto>
 *
 * // Use:
 * CqrsMutationStore<UserKey, User, UserProjection, UserAggregate, UserDto, UserPatch, UserDraft, UserPatchDto, UserDraftDto>
 * ```
 */

/**
 * Mutation store where all types are the same.
 *
 * Simplest possible mutation store configuration. Use for:
 * - In-memory caching
 * - Simple JSON APIs where response matches your domain model
 * - Prototyping
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type (used for all layers, including patches and drafts)
 *
 * ## Example
 * ```kotlin
 * val userStore: SimpleMutationStore<UserKey, User> = mutationStore {
 *     fetcher { key -> api.getUser(key.id) }
 *     mutations {
 *         update { key, user -> PatchClient.Response.Success(api.updateUser(key.id, user), null) }
 *         create { user -> PostClient.Response.Success(UserKey(user.id), user, null) }
 *         delete { key -> DeleteClient.Response.Success(api.deleteUser(key.id), null) }
 *     }
 * }
 * ```
 */
typealias SimpleMutationStore<Key, Domain> = RealMutationStore<
    Key,        // Key
    Domain,     // Domain
    Domain,     // ReadEntity = Domain
    Domain,     // WriteEntity = Domain
    Domain,     // NetworkResponse = Domain
    Domain,     // Patch = Domain
    Domain,     // Draft = Domain
    Domain,     // NetworkPatch = Domain
    Domain,     // NetworkDraft = Domain
    Domain      // NetworkPut = Domain
>

/**
 * Mutation store with separate domain, patch, draft, and entity types.
 *
 * Use when:
 * - Database schema differs from domain model
 * - Network JSON differs from domain model
 * - You have dedicated patch and draft types
 * - You need a conversion layer
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type
 * @param Patch The type for partial updates (PATCH operations)
 * @param Draft The type for creating new entities (POST operations)
 * @param Entity The unified database/network type
 *
 * ## Example
 * ```kotlin
 * val articleStore: BasicMutationStore<ArticleKey, Article, ArticlePatch, ArticleDraft, ArticleEntity> = mutationStore {
 *     fetcher { key -> api.getArticle(key.id) }
 *     converter = articleConverter  // Converts between Article ↔ ArticleEntity
 *     encoder = articleEncoder       // Converts patches/drafts to network format
 *     persistence {
 *         reader { key -> database.getArticle(key.id) }
 *         writer { key, entity -> database.save(entity) }
 *     }
 *     mutations {
 *         update { key, patch -> PatchClient.Response.Success(api.patchArticle(key.id, patch), null) }
 *         create { draft -> PostClient.Response.Success(ArticleKey(draft.id), api.createArticle(draft), null) }
 *         delete { key -> DeleteClient.Response.Success(api.deleteArticle(key.id), false, null) }
 *     }
 * }
 * ```
 */
typealias BasicMutationStore<Key, Domain, Patch, Draft, Entity> = RealMutationStore<
    Key,        // Key
    Domain,     // Domain
    Entity,     // ReadEntity
    Entity,     // WriteEntity = ReadEntity
    Entity,     // NetworkResponse = Entity
    Patch,      // Patch
    Draft,      // Draft
    Entity,     // NetworkPatch = Entity
    Entity,     // NetworkDraft = Entity
    Entity      // NetworkPut = Entity
>

/**
 * CQRS mutation store with separate read and write database types.
 *
 * Use when:
 * - Read model (projections) differs from write model (aggregates)
 * - Event sourcing architecture
 * - Denormalized reads, normalized writes
 * - Different network DTOs for different operations
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type
 * @param ReadEntity The database read projection type (query results from SoT)
 * @param WriteEntity The database write model type (what gets persisted to SoT)
 * @param NetworkResponse The type returned from network fetch operations
 * @param Patch The type for partial updates (PATCH operations)
 * @param Draft The type for creating new entities (POST operations)
 * @param NetworkPatch The network DTO for PATCH requests
 * @param NetworkDraft The network DTO for POST/create requests
 *
 * ## Example
 * ```kotlin
 * val cqrsStore: CqrsMutationStore<
 *     UserKey,
 *     UserView,           // Domain: denormalized view
 *     UserProjection,     // ReadEntity: query projection
 *     UserAggregate,      // WriteEntity: normalized aggregate
 *     UserDto,            // NetworkResponse: API response
 *     UserPatch,          // Patch: domain patch type
 *     UserDraft,          // Draft: domain creation type
 *     UserPatchDto,       // NetworkPatch: API patch format
 *     UserDraftDto        // NetworkDraft: API creation format
 * > = mutationStore {
 *     // ... CQRS configuration
 * }
 * ```
 */
typealias CqrsMutationStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft> = RealMutationStore<
    Key,            // Key
    Domain,         // Domain
    ReadEntity,     // Read database type
    WriteEntity,    // Write database type (different!)
    NetworkResponse, // Network response type
    Patch,          // Patch type
    Draft,          // Draft type
    NetworkPatch,   // Network patch type
    NetworkDraft,   // Network draft type
    NetworkResponse // NetworkPut = NetworkResponse
>

/**
 * Helper function to cast a MutationStore to its underlying RealMutationStore implementation.
 *
 * Useful when you need access to internal RealMutationStore features or
 * need to work around type inference issues.
 *
 * **Warning:** This is an escape hatch. Prefer using the MutationStore interface
 * when possible.
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type
 * @param ReadEntity The database read projection type
 * @param WriteEntity The database write model type
 * @param NetworkResponse The type returned from network fetch operations
 * @param Patch The type for partial updates
 * @param Draft The type for creating new entities
 * @param NetworkPatch The network DTO for PATCH requests
 * @param NetworkDraft The network DTO for POST/create requests
 * @param NetworkPut The network DTO for PUT/upsert requests
 */
@Suppress("UNCHECKED_CAST")
fun <Key : StoreKey, Domain : Any, ReadEntity, WriteEntity, NetworkResponse : Any, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut>
    MutationStore<Key, Domain, Patch, Draft>.asRealMutationStore(): RealMutationStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut> {
    return this as RealMutationStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse, Patch, Draft, NetworkPatch, NetworkDraft, NetworkPut>
}
