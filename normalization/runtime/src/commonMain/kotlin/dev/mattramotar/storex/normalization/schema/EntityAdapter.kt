package dev.mattramotar.storex.normalization.schema

import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.keys.EntityKey
import kotlin.reflect.KClass

interface EntityAdapter<T : Any> {
    val typeName: String
    fun extractId(entity: T): String
    fun key(entity: T) = EntityKey(typeName, extractId(entity))

    /** Return record + field mask for columns present in this source entity. */
    fun normalize(entity: T, ctx: NormalizationContext): Pair<NormalizedRecord, Set<String>>

    suspend fun denormalize(
        record: NormalizedRecord,
        ctx: DenormalizationContext
    ): T
}

/** Context for normalization (write path). */
interface NormalizationContext {
    /** Registers a nested entity for normalization. Returns its EntityKey. */
    fun registerNested(entity: Any): EntityKey
}

/** Context for denormalization (read path). */
interface DenormalizationContext {
    /** Resolves an entity reference. */
    suspend fun resolveReference(key: EntityKey): Any?
}

open class SchemaRegistry(private val byType: Map<String, EntityAdapter<*>>) {
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAdapter(typeName: String): EntityAdapter<T> = byType[typeName] as EntityAdapter<T>

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAdapter(k: KClass<T>): EntityAdapter<T> =
        byType.values.first { it is EntityAdapter<*> && it.typeName == k.simpleName } as EntityAdapter<T>
}
