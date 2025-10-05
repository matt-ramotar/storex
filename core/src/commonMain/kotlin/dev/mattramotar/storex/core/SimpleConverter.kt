package dev.mattramotar.storex.core

import dev.mattramotar.storex.core.Converter.NetMeta

/**
 * Simplified converter for the common case where read and write persistence types are the same.
 *
 * Reduces [Converter]'s 5 generic parameters to 3 by collapsing:
 * - `ReadEntity` and `WriteEntity` → `Entity`
 * - `NetworkResponse` → `Entity` (assuming network returns same format as persistence)
 *
 * Use when:
 * - Your database uses the same type for reads and writes (no CQRS)
 * - Network API returns same format you persist
 * - You need domain ↔ persistence conversion only
 *
 * @param Key The [StoreKey] subtype identifying stored entities
 * @param Domain The application's domain model type - what your app works with (e.g., User, Article)
 * @param Entity The unified database/network type - what gets persisted and transferred (e.g., UserEntity, ArticleJson)
 *
 * @see Converter For full 5-parameter version with separate ReadEntity/WriteEntity/NetworkResponse
 * @see IdentityConverter For when Domain == Entity (no conversion needed)
 *
 * ## Example: Same Type Everywhere
 * ```kotlin
 * // When domain == network == database (simplest case)
 * class IdentityConverter<Key : StoreKey, Domain> : SimpleConverter<Key, Domain, Domain> {
 *     override suspend fun toDomain(key: Key, value: Domain): Domain = value
 *     override suspend fun fromDomain(key: Key, value: Domain): Domain = value
 *     override suspend fun fromNetwork(key: Key, network: Domain): Domain = network
 * }
 * ```
 *
 * ## Example: Different Network and Domain Types
 * ```kotlin
 * // When network JSON differs from domain model
 * class UserConverter : SimpleConverter<UserKey, User, UserJson> {
 *     override suspend fun toDomain(key: UserKey, value: UserJson): User {
 *         return User(
 *             id = value.id,
 *             name = value.full_name,  // Different field names
 *             email = value.email_address
 *         )
 *     }
 *
 *     override suspend fun fromDomain(key: UserKey, value: User): UserJson {
 *         return UserJson(
 *             id = value.id,
 *             full_name = value.name,
 *             email_address = value.email
 *         )
 *     }
 *
 *     override suspend fun fromNetwork(key: UserKey, network: UserJson): UserJson {
 *         return network  // Store network format in database
 *     }
 * }
 * ```
 *
 * ## Example: Separate Domain and Persistence Types
 * ```kotlin
 * // When database has different schema than domain
 * class ArticleConverter : SimpleConverter<ArticleKey, Article, ArticleEntity> {
 *     override suspend fun toDomain(key: ArticleKey, value: ArticleEntity): Article {
 *         return Article(
 *             id = value.id,
 *             title = value.title,
 *             author = Author(value.authorId, value.authorName),  // Denormalized
 *             publishedAt = Instant.parse(value.publishedAtIso)   // Type conversion
 *         )
 *     }
 *
 *     override suspend fun fromDomain(key: ArticleKey, value: Article): ArticleEntity {
 *         return ArticleEntity(
 *             id = value.id,
 *             title = value.title,
 *             authorId = value.author.id,  // Normalized
 *             authorName = value.author.name,
 *             publishedAtIso = value.publishedAt.toString()  // Serialized
 *         )
 *     }
 *
 *     override suspend fun fromNetwork(key: ArticleKey, network: ArticleEntity): ArticleEntity {
 *         return network
 *     }
 * }
 * ```
 */
interface SimpleConverter<Key : StoreKey, Domain, Entity> {

    /**
     * Converts database/persistence format to domain model.
     *
     * Called when:
     * - Reading from Source of Truth
     * - After fetching from network and storing to database
     *
     * @param key The store key
     * @param value The database/persisted value
     * @return The domain model
     */
    suspend fun toDomain(key: Key, value: Entity): Domain

    /**
     * Converts domain model to database/persistence format.
     *
     * Called when:
     * - Writing to Source of Truth (creates, updates, upserts)
     * - Storing optimistic updates
     *
     * @param key The store key
     * @param value The domain model
     * @return The database value, or null if conversion not possible
     */
    suspend fun fromDomain(key: Key, value: Domain): Entity?

    /**
     * Converts network response to database/persistence format.
     *
     * Called when:
     * - Fetching from network
     * - Receiving server responses for mutations
     *
     * @param key The store key
     * @param network The network response
     * @return The database value to persist
     */
    suspend fun fromNetwork(key: Key, network: Entity): Entity

    /**
     * Extracts metadata from database value (e.g., updatedAt timestamp).
     *
     * Used for freshness validation. Return null if no metadata available.
     *
     * @param value The database value
     * @return Metadata object (typically containing updatedAt timestamp)
     */
    suspend fun extractMetadata(value: Entity): Any? = null

    /**
     * Extracts metadata from network response (e.g., etag, lastModified).
     *
     * Used for conditional requests and freshness validation.
     *
     * @param network The network response
     * @return Network metadata
     */
    suspend fun extractNetworkMetadata(network: Entity): NetMeta = NetMeta()
}

/**
 * Identity converter for when domain, network, and database types are all the same.
 *
 * Use this when:
 * - Your API returns exactly what your app uses
 * - Your database stores exactly what your app uses
 * - No transformation needed
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type (same as Entity in this case)
 *
 * ## Example
 * ```kotlin
 * val store = store<UserKey, User> {
 *     fetcher { key -> api.getUser(key.id) }
 *     converter = identityConverter()
 * }
 * ```
 */
class IdentityConverter<Key : StoreKey, Domain> : SimpleConverter<Key, Domain, Domain> {
    override suspend fun toDomain(key: Key, value: Domain): Domain = value
    override suspend fun fromDomain(key: Key, value: Domain): Domain = value
    override suspend fun fromNetwork(key: Key, network: Domain): Domain = network
}

/**
 * Creates an identity converter that passes values through unchanged.
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type
 */
fun <Key : StoreKey, Domain> identityConverter(): SimpleConverter<Key, Domain, Domain> = IdentityConverter()

/**
 * Adapter to use a [SimpleConverter] as a [Converter] (for backward compatibility).
 *
 * Expands [SimpleConverter]'s 3 parameters to [Converter]'s 5 parameters by:
 * - Setting ReadEntity = Entity
 * - Setting WriteEntity = Entity
 * - Setting NetworkResponse = Entity
 *
 * @param Key The [StoreKey] subtype
 * @param Domain The application's domain model type
 * @param Entity The unified database/network type
 * @param simple The simple converter to adapt
 */
internal class SimpleConverterAdapter<Key : StoreKey, Domain, Entity>(
    private val simple: SimpleConverter<Key, Domain, Entity>
) : Converter<Key, Domain, Entity, Entity, Entity> {

    override suspend fun netToDbWrite(key: Key, net: Entity): Entity {
        return simple.fromNetwork(key, net)
    }

    override suspend fun dbReadToDomain(key: Key, db: Entity): Domain {
        return simple.toDomain(key, db)
    }

    override suspend fun dbMetaFromProjection(db: Entity): Any? {
        return simple.extractMetadata(db)
    }

    override suspend fun netMeta(net: Entity): Converter.NetMeta {
        return simple.extractNetworkMetadata(net)
    }

    override suspend fun domainToDbWrite(key: Key, value: Domain): Entity? {
        return simple.fromDomain(key, value)
    }
}
