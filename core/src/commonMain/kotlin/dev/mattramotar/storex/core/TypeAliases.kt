package dev.mattramotar.storex.core

import dev.mattramotar.storex.core.internal.RealReadStore

/**
 * Type aliases to reduce generic parameter explosion in common cases.
 *
 * ## Problem
 *
 * The full [RealReadStore] implementation has 5 generic parameters:
 * ```
 * RealReadStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse>
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
 * ### 1. SimpleReadStore - When all types are the same
 *
 * Use when:
 * - Your domain, network, and database types are identical
 * - No transformation needed
 * - In-memory only, or simple JSON storage
 *
 * ```kotlin
 * // Instead of:
 * RealReadStore<UserKey, User, User, User, User>
 *
 * // Use:
 * SimpleReadStore<UserKey, User>
 * ```
 *
 * ### 2. BasicReadStore - Separate domain and persistence
 *
 * Use when:
 * - Domain type differs from database/network type
 * - Single conversion layer (Entity type used for both read and write)
 * - Most common pattern
 *
 * ```kotlin
 * // Instead of:
 * RealReadStore<UserKey, User, UserEntity, UserEntity, UserEntity>
 *
 * // Use:
 * BasicReadStore<UserKey, User, UserEntity>
 * ```
 *
 * ### 3. CqrsReadStore - CQRS with separate read/write models
 *
 * Use when:
 * - Read model (projections) differs from write model (aggregates)
 * - Event sourcing architecture
 * - Denormalized reads, normalized writes
 *
 * ```kotlin
 * // Instead of:
 * RealReadStore<UserKey, User, UserProjection, UserAggregate, UserDto>
 *
 * // Use:
 * CqrsReadStore<UserKey, User, UserProjection, UserAggregate, UserDto>
 * ```
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
typealias SimpleReadStore<Key, Domain> = RealReadStore<
    Key,        // Key
    Domain,     // Domain value
    Domain,     // ReadEntity = Domain
    Domain,     // WriteEntity = Domain
    Domain      // NetworkResponse = Domain
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
typealias BasicReadStore<Key, Domain, Entity> = RealReadStore<
    Key,        // Key
    Domain,     // Domain value
    Entity,     // ReadEntity
    Entity,     // WriteEntity = ReadEntity
    Entity      // NetworkResponse = Entity
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
 * @param NetworkResponse The type returned from network fetch operations
 *
 * ## Example
 * ```kotlin
 * val cqrsStore: CqrsReadStore<
 *     UserKey,
 *     UserView,           // Read: denormalized view
 *     UserProjection,     // ReadEntity: query projection
 *     UserAggregate,      // WriteEntity: normalized aggregate
 *     UserDto             // NetworkResponse: API response
 * > = store {
 *     // ... CQRS configuration
 * }
 * ```
 */
typealias CqrsReadStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse> = RealReadStore<
    Key,            // Key
    Domain,         // Domain value
    ReadEntity,     // Read database type
    WriteEntity,    // Write database type (different!)
    NetworkResponse // Network response type
>

/**
 * Helper function to cast a Store to its underlying RealReadStore implementation.
 *
 * Useful when you need access to internal RealReadStore features or
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
 */
@Suppress("UNCHECKED_CAST")
fun <Key : StoreKey, Domain : Any, ReadEntity, WriteEntity, NetworkResponse : Any>
    Store<Key, Domain>.asRealReadStore(): RealReadStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse> {
    return this as RealReadStore<Key, Domain, ReadEntity, WriteEntity, NetworkResponse>
}
