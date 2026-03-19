package dev.mattramotar.storex.normalization.schema

import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.format.NormalizedValue
import kotlin.test.Test
import kotlin.test.assertSame

class SchemaRegistryTest {
    private data class CustomEntity(val id: String)

    private object CustomEntityAdapter : EntityAdapter<CustomEntity> {
        override val typeName: String = "CanonicalCustomEntity"

        override fun extractId(entity: CustomEntity): String = entity.id

        override fun normalize(
            entity: CustomEntity,
            ctx: NormalizationContext,
        ): Pair<NormalizedRecord, Set<String>> =
            mapOf("id" to NormalizedValue.Scalar(entity.id)) to setOf("id")

        override suspend fun denormalize(
            record: NormalizedRecord,
            ctx: DenormalizationContext,
        ): CustomEntity {
            val scalar = record["id"] as? NormalizedValue.Scalar
                ?: error("Missing id field")
            return CustomEntity(id = scalar.value as String)
        }
    }

    @Test
    fun getAdapterByClass_respectsCustomTypeName() {
        val registry = SchemaRegistry(
            byType = mapOf(CustomEntityAdapter.typeName to CustomEntityAdapter),
            byClass = mapOf(CustomEntity::class to CustomEntityAdapter),
        )

        val adapter = registry.getAdapter(CustomEntity::class)

        assertSame(CustomEntityAdapter, adapter)
    }
}
