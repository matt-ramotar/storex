package dev.mattramotar.storex.store.dsl

import dev.mattramotar.storex.store.SimpleConverter
import dev.mattramotar.storex.store.StoreKey
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Configuration for value conversion between domain, network, and persistence layers.
 *
 * @param K The store key type
 * @param V The domain value type
 * @param Db The database/network type
 */
class ConverterConfig<K : StoreKey, V : Any, Db> {
    /**
     * The converter to use for transforming values between layers.
     * If null, an identity converter is used (assumes V == Db).
     */
    var converter: SimpleConverter<K, V, Db>? = null

    /**
     * Set the converter using a builder function.
     *
     * Example:
     * ```kotlin
     * converter { converter ->
     *     object : SimpleConverter<UserKey, User, UserEntity> {
     *         override suspend fun toDomain(key: UserKey, value: UserEntity): User {
     *             return User(value.id, value.name, value.email)
     *         }
     *
     *         override suspend fun fromDomain(key: UserKey, value: User): UserEntity {
     *             return UserEntity(value.id, value.name, value.email)
     *         }
     *
     *         override suspend fun fromNetwork(key: UserKey, network: UserEntity): UserEntity {
     *             return network
     *         }
     *     }
     * }
     * ```
     */
    fun converter(converter: SimpleConverter<K, V, Db>) {
        this.converter = converter
    }
}

/**
 * Configuration for in-memory caching behavior.
 */
class CacheConfig {
    /**
     * Maximum number of items to keep in the cache.
     * Default: 100
     */
    var maxSize: Int = 100

    /**
     * Time-to-live for cached items. After this duration, cached items are considered expired.
     * Default: Duration.INFINITE (no expiration)
     */
    var ttl: Duration = Duration.INFINITE
}

/**
 * Configuration for freshness validation and conditional fetching.
 */
class FreshnessConfig {
    /**
     * Default TTL for data freshness. Data older than this is considered stale.
     * Default: 5 minutes
     */
    var ttl: Duration = 5.minutes

    /**
     * If set, allows serving stale data when network requests fail, as long as the data
     * is not older than this duration.
     * Default: null (disabled)
     */
    var staleIfError: Duration? = null
}

/**
 * Configuration for local persistence (database, file system, etc.).
 *
 * @param K The store key type
 * @param V The domain value type
 */
class PersistenceConfig<K : StoreKey, V : Any> {
    /**
     * Function to read a value from persistence. Returns null if not found.
     */
    var reader: (suspend (K) -> V?)? = null

    /**
     * Function to write a value to persistence.
     */
    var writer: (suspend (K, V) -> Unit)? = null

    /**
     * Function to delete a value from persistence.
     */
    var deleter: (suspend (K) -> Unit)? = null

    /**
     * Function to execute multiple persistence operations in a transaction.
     * Default: executes the block directly without transaction support.
     */
    var transactional: (suspend (suspend () -> Unit) -> Unit)? = { block -> block() }

    /**
     * Configure the reader function.
     */
    fun reader(read: suspend (K) -> V?) {
        reader = read
    }

    /**
     * Configure the writer function.
     */
    fun writer(write: suspend (K, V) -> Unit) {
        writer = write
    }

    /**
     * Configure the deleter function.
     */
    fun deleter(delete: suspend (K) -> Unit) {
        deleter = delete
    }

    /**
     * Configure transactional support.
     */
    fun transactional(tx: suspend (suspend () -> Unit) -> Unit) {
        transactional = tx
    }
}
