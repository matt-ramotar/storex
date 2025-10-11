package dev.mattramotar.storex.normalization

import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.format.NormalizedValue
import dev.mattramotar.storex.normalization.keys.EntityKey
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChangeSetBuilderTest {

    private fun createTestRecord(vararg pairs: Pair<String, Any?>): NormalizedRecord {
        return pairs.associate { (key, value) ->
            key to when (value) {
                is EntityKey -> NormalizedValue.Ref(value)
                is List<*> -> {
                    if (value.firstOrNull() is EntityKey) {
                        @Suppress("UNCHECKED_CAST")
                        NormalizedValue.RefList(value as List<EntityKey>)
                    } else {
                        NormalizedValue.ScalarList(value)
                    }
                }
                else -> NormalizedValue.Scalar(value)
            }
        }
    }

    private fun createTestKey(typeName: String = "User", id: String = "1") = EntityKey(typeName, id)

    private fun createTestMeta(
        etag: String? = null,
        updatedAt: kotlinx.datetime.Instant = Clock.System.now(),
        tombstone: Boolean = false,
        tags: Set<String> = emptySet()
    ) = EntityMeta(etag = etag, updatedAt = updatedAt, tombstone = tombstone, tags = tags)

    // ===== build() tests =====

    @Test
    fun build_givenEmptyBuilder_whenBuild_thenReturnsEmptyChangeSet() {
        // Given
        val builder = ChangeSetBuilder()

        // When
        val result = builder.build()

        // Then
        assertEquals(emptyMap(), result.upserts)
        assertEquals(emptySet(), result.deletes)
        assertEquals(emptyList(), result.rekeys)
        assertEquals(emptyMap(), result.fieldMasks)
        assertEquals(emptyMap(), result.meta)
    }

    // ===== upsert() tests =====

    @Test
    fun upsert_givenKeyAndRecordWithoutMetadata_whenUpsert_thenAddsUpsertWithoutMeta() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice", "age" to 30)

        // When
        val result = builder.upsert(key, record).build()

        // Then
        assertEquals(1, result.upserts.size)
        assertEquals(record, result.upserts[key])
        assertEquals(emptyMap(), result.meta)
    }

    @Test
    fun upsert_givenKeyAndRecordWithMetadata_whenUpsert_thenAddsUpsertWithMeta() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")
        val meta = createTestMeta(etag = "v1")

        // When
        val result = builder.upsert(key, record, meta).build()

        // Then
        assertEquals(1, result.upserts.size)
        assertEquals(record, result.upserts[key])
        assertEquals(1, result.meta.size)
        assertEquals(meta, result.meta[key])
    }

    @Test
    fun upsert_givenFluentChain_whenUpsert_thenReturnsBuilderForChaining() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val record1 = createTestRecord("name" to "Alice")
        val record2 = createTestRecord("name" to "Bob")

        // When
        val returnedBuilder = builder.upsert(key1, record1)

        // Then
        assertSame(builder, returnedBuilder)

        // When - chain multiple operations
        val result = builder
            .upsert(key1, record1)
            .upsert(key2, record2)
            .build()

        // Then
        assertEquals(2, result.upserts.size)
        assertEquals(record1, result.upserts[key1])
        assertEquals(record2, result.upserts[key2])
    }

    @Test
    fun upsert_givenMultipleUpserts_whenBuild_thenContainsAllUpserts() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("Post", "100")
        val key3 = createTestKey("Comment", "200")
        val record1 = createTestRecord("name" to "Alice")
        val record2 = createTestRecord("title" to "Post 1")
        val record3 = createTestRecord("text" to "Comment 1")

        // When
        builder.upsert(key1, record1)
        builder.upsert(key2, record2)
        builder.upsert(key3, record3)
        val result = builder.build()

        // Then
        assertEquals(3, result.upserts.size)
        assertEquals(record1, result.upserts[key1])
        assertEquals(record2, result.upserts[key2])
        assertEquals(record3, result.upserts[key3])
    }

    @Test
    fun upsert_givenSameKeyMultipleTimes_whenBuild_thenLastUpsertWins() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val record1 = createTestRecord("name" to "Alice")
        val record2 = createTestRecord("name" to "Alice Updated")
        val meta1 = createTestMeta(etag = "v1")
        val meta2 = createTestMeta(etag = "v2")

        // When
        builder.upsert(key, record1, meta1)
        builder.upsert(key, record2, meta2)
        val result = builder.build()

        // Then
        assertEquals(1, result.upserts.size)
        assertEquals(record2, result.upserts[key])
        assertEquals(meta2, result.meta[key])
    }

    // ===== patch() tests =====

    @Test
    fun patch_givenKeyRecordAndMaskWithoutMetadata_whenPatch_thenAddsUpsertWithMaskWithoutMeta() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice Updated", "age" to 31)
        val mask = setOf("name")

        // When
        val result = builder.patch(key, record, mask).build()

        // Then
        assertEquals(1, result.upserts.size)
        assertEquals(record, result.upserts[key])
        assertEquals(1, result.fieldMasks.size)
        assertEquals(mask, result.fieldMasks[key])
        assertEquals(emptyMap(), result.meta)
    }

    @Test
    fun patch_givenKeyRecordAndMaskWithMetadata_whenPatch_thenAddsUpsertWithMaskAndMeta() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice Updated")
        val mask = setOf("name")
        val meta = createTestMeta(etag = "v2")

        // When
        val result = builder.patch(key, record, mask, meta).build()

        // Then
        assertEquals(1, result.upserts.size)
        assertEquals(record, result.upserts[key])
        assertEquals(1, result.fieldMasks.size)
        assertEquals(mask, result.fieldMasks[key])
        assertEquals(1, result.meta.size)
        assertEquals(meta, result.meta[key])
    }

    @Test
    fun patch_givenFluentChain_whenPatch_thenReturnsBuilderForChaining() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val record1 = createTestRecord("name" to "Alice")
        val record2 = createTestRecord("name" to "Bob")
        val mask1 = setOf("name")
        val mask2 = setOf("name")

        // When
        val returnedBuilder = builder.patch(key1, record1, mask1)

        // Then
        assertSame(builder, returnedBuilder)

        // When - chain multiple operations
        val result = builder
            .patch(key1, record1, mask1)
            .patch(key2, record2, mask2)
            .build()

        // Then
        assertEquals(2, result.upserts.size)
        assertEquals(2, result.fieldMasks.size)
    }

    @Test
    fun patch_givenMultiplePatches_whenBuild_thenContainsAllPatches() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val record1 = createTestRecord("name" to "Alice")
        val record2 = createTestRecord("email" to "bob@example.com")
        val mask1 = setOf("name")
        val mask2 = setOf("email")

        // When
        builder.patch(key1, record1, mask1)
        builder.patch(key2, record2, mask2)
        val result = builder.build()

        // Then
        assertEquals(2, result.upserts.size)
        assertEquals(2, result.fieldMasks.size)
        assertEquals(mask1, result.fieldMasks[key1])
        assertEquals(mask2, result.fieldMasks[key2])
    }

    @Test
    fun patch_givenEmptyMask_whenPatch_thenStoresEmptyMask() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")
        val emptyMask = emptySet<String>()

        // When
        val result = builder.patch(key, record, emptyMask).build()

        // Then
        assertEquals(1, result.fieldMasks.size)
        assertTrue(result.fieldMasks[key]?.isEmpty() == true)
    }

    // ===== delete() tests =====

    @Test
    fun delete_givenKey_whenDelete_thenAddsDelete() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")

        // When
        val result = builder.delete(key).build()

        // Then
        assertEquals(1, result.deletes.size)
        assertTrue(result.deletes.contains(key))
    }

    @Test
    fun delete_givenFluentChain_whenDelete_thenReturnsBuilderForChaining() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")

        // When
        val returnedBuilder = builder.delete(key1)

        // Then
        assertSame(builder, returnedBuilder)

        // When - chain multiple operations
        val result = builder
            .delete(key1)
            .delete(key2)
            .build()

        // Then
        assertEquals(2, result.deletes.size)
        assertTrue(result.deletes.contains(key1))
        assertTrue(result.deletes.contains(key2))
    }

    @Test
    fun delete_givenMultipleDeletes_whenBuild_thenContainsAllDeletes() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("Post", "100")
        val key3 = createTestKey("Comment", "200")

        // When
        builder.delete(key1)
        builder.delete(key2)
        builder.delete(key3)
        val result = builder.build()

        // Then
        assertEquals(3, result.deletes.size)
        assertTrue(result.deletes.contains(key1))
        assertTrue(result.deletes.contains(key2))
        assertTrue(result.deletes.contains(key3))
    }

    @Test
    fun delete_givenSameKeyMultipleTimes_whenBuild_thenSetContainsKeyOnce() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")

        // When
        builder.delete(key)
        builder.delete(key)
        builder.delete(key)
        val result = builder.build()

        // Then
        assertEquals(1, result.deletes.size)
        assertTrue(result.deletes.contains(key))
    }

    // ===== rekey() tests =====

    @Test
    fun rekey_givenOldAndNewKey_whenRekey_thenAddsRekey() {
        // Given
        val builder = ChangeSetBuilder()
        val oldKey = createTestKey("User", "temp-1")
        val newKey = createTestKey("User", "1")

        // When
        val result = builder.rekey(oldKey, newKey).build()

        // Then
        assertEquals(1, result.rekeys.size)
        assertEquals(oldKey, result.rekeys[0].oldKey)
        assertEquals(newKey, result.rekeys[0].newKey)
    }

    @Test
    fun rekey_givenFluentChain_whenRekey_thenReturnsBuilderForChaining() {
        // Given
        val builder = ChangeSetBuilder()
        val oldKey1 = createTestKey("User", "temp-1")
        val newKey1 = createTestKey("User", "1")
        val oldKey2 = createTestKey("User", "temp-2")
        val newKey2 = createTestKey("User", "2")

        // When
        val returnedBuilder = builder.rekey(oldKey1, newKey1)

        // Then
        assertSame(builder, returnedBuilder)

        // When - chain multiple operations (note: oldKey1->newKey1 already added above)
        val result = builder
            .rekey(oldKey2, newKey2)
            .build()

        // Then
        assertEquals(2, result.rekeys.size)
    }

    @Test
    fun rekey_givenMultipleRekeys_whenBuild_thenContainsAllRekeysInOrder() {
        // Given
        val builder = ChangeSetBuilder()
        val oldKey1 = createTestKey("User", "temp-1")
        val newKey1 = createTestKey("User", "1")
        val oldKey2 = createTestKey("Post", "temp-100")
        val newKey2 = createTestKey("Post", "100")
        val oldKey3 = createTestKey("Comment", "temp-200")
        val newKey3 = createTestKey("Comment", "200")

        // When
        builder.rekey(oldKey1, newKey1)
        builder.rekey(oldKey2, newKey2)
        builder.rekey(oldKey3, newKey3)
        val result = builder.build()

        // Then
        assertEquals(3, result.rekeys.size)
        assertEquals(oldKey1, result.rekeys[0].oldKey)
        assertEquals(newKey1, result.rekeys[0].newKey)
        assertEquals(oldKey2, result.rekeys[1].oldKey)
        assertEquals(newKey2, result.rekeys[1].newKey)
        assertEquals(oldKey3, result.rekeys[2].oldKey)
        assertEquals(newKey3, result.rekeys[2].newKey)
    }

    @Test
    fun rekey_givenChainedRekeys_whenBuild_thenPreservesOrder() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val key3 = createTestKey("User", "3")

        // When
        val result = builder
            .rekey(key1, key2)
            .rekey(key2, key3)
            .build()

        // Then
        assertEquals(2, result.rekeys.size)
        assertEquals(key1, result.rekeys[0].oldKey)
        assertEquals(key2, result.rekeys[0].newKey)
        assertEquals(key2, result.rekeys[1].oldKey)
        assertEquals(key3, result.rekeys[1].newKey)
    }

    // ===== Mixed operations tests =====

    @Test
    fun build_givenMixedOperations_whenBuild_thenContainsAllOperations() {
        // Given
        val builder = ChangeSetBuilder()
        val upsertKey = createTestKey("User", "1")
        val patchKey = createTestKey("User", "2")
        val deleteKey = createTestKey("User", "3")
        val oldRekeyKey = createTestKey("User", "temp-4")
        val newRekeyKey = createTestKey("User", "4")

        val upsertRecord = createTestRecord("name" to "Alice")
        val patchRecord = createTestRecord("name" to "Bob Updated")
        val patchMask = setOf("name")
        val meta = createTestMeta(etag = "v1")

        // When
        val result = builder
            .upsert(upsertKey, upsertRecord, meta)
            .patch(patchKey, patchRecord, patchMask)
            .delete(deleteKey)
            .rekey(oldRekeyKey, newRekeyKey)
            .build()

        // Then
        assertEquals(2, result.upserts.size)
        assertEquals(1, result.fieldMasks.size)
        assertEquals(1, result.deletes.size)
        assertEquals(1, result.rekeys.size)
        assertEquals(1, result.meta.size)
        assertEquals(upsertRecord, result.upserts[upsertKey])
        assertEquals(patchRecord, result.upserts[patchKey])
        assertEquals(patchMask, result.fieldMasks[patchKey])
        assertTrue(result.deletes.contains(deleteKey))
        assertEquals(oldRekeyKey, result.rekeys[0].oldKey)
        assertEquals(newRekeyKey, result.rekeys[0].newKey)
    }

    @Test
    fun build_givenUpsertAfterPatchSameKey_whenBuild_thenUpsertOverwritesPatchMask() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val patchRecord = createTestRecord("name" to "Alice")
        val patchMask = setOf("name")
        val upsertRecord = createTestRecord("name" to "Alice", "age" to 30)

        // When
        builder.patch(key, patchRecord, patchMask)
        builder.upsert(key, upsertRecord)
        val result = builder.build()

        // Then
        assertEquals(1, result.upserts.size)
        assertEquals(upsertRecord, result.upserts[key])
        // Field mask still exists from patch
        assertEquals(patchMask, result.fieldMasks[key])
    }

    @Test
    fun build_givenPatchAfterUpsertSameKey_whenBuild_thenPatchOverwritesUpsert() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val upsertRecord = createTestRecord("name" to "Alice", "age" to 30)
        val patchRecord = createTestRecord("name" to "Alice Updated")
        val patchMask = setOf("name")

        // When
        builder.upsert(key, upsertRecord)
        builder.patch(key, patchRecord, patchMask)
        val result = builder.build()

        // Then
        assertEquals(1, result.upserts.size)
        assertEquals(patchRecord, result.upserts[key])
        assertEquals(patchMask, result.fieldMasks[key])
    }

    // ===== Multiple build() tests =====

    @Test
    fun build_givenMultipleBuildCalls_whenBuild_thenEachReturnsCurrentState() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val record1 = createTestRecord("name" to "Alice")
        val record2 = createTestRecord("name" to "Bob")

        // When
        builder.upsert(key1, record1)
        val firstBuild = builder.build()

        builder.upsert(key2, record2)
        val secondBuild = builder.build()

        // Then - build() returns mutable references, so both reflect current state
        assertEquals(2, firstBuild.upserts.size)
        assertEquals(record1, firstBuild.upserts[key1])
        assertEquals(record2, firstBuild.upserts[key2])

        assertEquals(2, secondBuild.upserts.size)
        assertEquals(record1, secondBuild.upserts[key1])
        assertEquals(record2, secondBuild.upserts[key2])
    }

    @Test
    fun build_givenBuildCalledMultipleTimes_whenBuild_thenDoesNotClearBuilder() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")

        // When
        builder.upsert(key, record)
        val firstBuild = builder.build()
        val secondBuild = builder.build()

        // Then
        assertEquals(firstBuild.upserts, secondBuild.upserts)
        assertEquals(1, secondBuild.upserts.size)
    }

    // ===== Edge cases and comprehensive coverage tests =====

    @Test
    fun build_givenComplexMixedOperations_whenBuild_thenCorrectlyBuildsChangeSet() {
        // Given
        val builder = ChangeSetBuilder()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("Post", "100")
        val key3 = createTestKey("Comment", "200")
        val key4 = createTestKey("User", "temp-4")
        val key5 = createTestKey("User", "4")
        val key6 = createTestKey("User", "5")

        val record1 = createTestRecord("name" to "Alice", "age" to 30)
        val record2 = createTestRecord("title" to "My Post", "body" to "Content")
        val record3 = createTestRecord("text" to "Nice post")
        val meta1 = createTestMeta(etag = "v1", tags = setOf("user", "active"))
        val meta2 = createTestMeta(etag = "v2")

        // When
        val result = builder
            .upsert(key1, record1, meta1)
            .patch(key2, record2, setOf("title", "body"), meta2)
            .delete(key3)
            .rekey(key4, key5)
            .upsert(key6, record3)
            .delete(key1) // Delete after upsert - should have both operations
            .build()

        // Then
        assertEquals(3, result.upserts.size) // key1, key2, key6
        assertEquals(2, result.deletes.size) // key3, key1
        assertEquals(1, result.rekeys.size) // key4 -> key5
        assertEquals(1, result.fieldMasks.size) // key2
        assertEquals(2, result.meta.size) // key1, key2

        assertTrue(result.deletes.contains(key1))
        assertTrue(result.deletes.contains(key3))
        assertEquals(setOf("title", "body"), result.fieldMasks[key2])
    }

    @Test
    fun build_givenMetadataWithAllFields_whenBuild_thenPreservesAllMetadataFields() {
        // Given
        val builder = ChangeSetBuilder()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")
        val meta = createTestMeta(
            etag = "v1",
            updatedAt = Clock.System.now(),
            tombstone = true,
            tags = setOf("archived", "admin")
        )

        // When
        val result = builder.upsert(key, record, meta).build()

        // Then
        val resultMeta = result.meta[key]
        assertEquals("v1", resultMeta?.etag)
        assertEquals(true, resultMeta?.tombstone)
        assertEquals(setOf("archived", "admin"), resultMeta?.tags)
    }
}
