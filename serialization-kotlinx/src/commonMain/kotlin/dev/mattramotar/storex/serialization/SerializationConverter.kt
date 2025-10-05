package dev.mattramotar.storex.serialization

import dev.mattramotar.storex.core.Converter
import dev.mattramotar.storex.core.StoreKey
import kotlinx.serialization.KSerializer

/**
 * Automatic converter for types annotated with @Serializable.
 *
 * This module provides zero-boilerplate converters for kotlinx.serialization types,
 * eliminating the need to write manual conversion logic.
 *
 * **Planned Features** (to be implemented):
 * - Automatic domain ↔ entity conversion for @Serializable types
 * - JSON SourceOfTruth implementation with kotlinx.serialization
 * - Automatic network response deserialization
 * - Polymorphic serialization support
 * - Custom serializer integration
 *
 * Example usage (future):
 * ```kotlin
 * @Serializable
 * data class User(val id: String, val name: String)
 *
 * @Serializable
 * data class UserEntity(val id: String, val name: String, val cachedAt: Long)
 *
 * val converter = serializableConverter<UserKey, User, UserEntity>(
 *     domainSerializer = User.serializer(),
 *     entitySerializer = UserEntity.serializer()
 * ) { domain ->
 *     UserEntity(
 *         id = domain.id,
 *         name = domain.name,
 *         cachedAt = Clock.System.now().toEpochMilliseconds()
 *     )
 * }
 * ```
 *
 * @param K The store key type
 * @param Domain The domain type (must be @Serializable)
 * @param Entity The persistence entity type (must be @Serializable)
 */
interface SerializableConverter<K : StoreKey, Domain, Entity, Network> :
    Converter<K, Domain, Entity, Network, Entity> {

    /**
     * The serializer for the domain type.
     */
    val domainSerializer: KSerializer<Domain>

    /**
     * The serializer for the entity type.
     */
    val entitySerializer: KSerializer<Entity>

    /**
     * The serializer for the network type.
     */
    val networkSerializer: KSerializer<Network>
}

// TODO: Implement the following in future phases:
// - serializableConverter(): Factory function for creating converters
// - JsonSourceOfTruth: SourceOfTruth implementation using kotlinx.serialization.json
// - AutoConverter: Automatic conversion when Domain == Entity
// - PolymorphicConverter: Support for sealed classes and polymorphism
// - NetworkResponseDeserializer: Automatic network response handling
