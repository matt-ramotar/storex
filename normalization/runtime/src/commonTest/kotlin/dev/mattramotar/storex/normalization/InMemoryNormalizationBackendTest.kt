package dev.mattramotar.storex.normalization

import app.cash.turbine.test
import dev.mattramotar.storex.core.QueryKey
import dev.mattramotar.storex.core.StoreNamespace
import dev.mattramotar.storex.normalization.backend.RootRef
import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.format.NormalizedValue
import dev.mattramotar.storex.normalization.keys.EntityKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryNormalizationBackendTest {

    private fun createBackend() = InMemoryNormalizationBackend()

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

    private fun createTestRootRef(requestKey: QueryKey = QueryKey(StoreNamespace("test"), mapOf("query" to "query1")), shapeId: String = "shape1") =
        RootRef(requestKey, ShapeId(shapeId))

    private fun createTestMeta(
        etag: String? = null,
        updatedAt: kotlinx.datetime.Instant = Clock.System.now(),
        tombstone: Boolean = false
    ) = EntityMeta(etag = etag, updatedAt = updatedAt, tombstone = tombstone)

    // ===== read() tests =====

    @Test
    fun read_givenEmptyKeys_whenRead_thenReturnsEmptyMap() = runTest {
        // Given
        val backend = createBackend()

        // When
        val result = backend.read(emptySet())

        // Then
        assertEquals(emptyMap(), result)
    }

    @Test
    fun read_givenNonExistentKey_whenRead_thenReturnsNullForKey() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "999")

        // When
        val result = backend.read(setOf(key))

        // Then
        assertEquals(1, result.size)
        assertNull(result[key])
    }

    @Test
    fun read_givenExistingKey_whenRead_thenReturnsRecord() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice", "age" to 30)
        val changeSet = NormalizedChangeSet(
            upserts = mapOf(key to record),
            meta = mapOf(key to createTestMeta())
        )
        backend.apply(changeSet)

        // When
        val result = backend.read(setOf(key))

        // Then
        assertNotNull(result[key])
        assertEquals(record, result[key])
    }

    @Test
    fun read_givenMultipleKeys_whenReadMixed_thenReturnsCorrectMappings() = runTest {
        // Given
        val backend = createBackend()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val key3 = createTestKey("User", "999")
        val record1 = createTestRecord("name" to "Alice")
        val record2 = createTestRecord("name" to "Bob")

        val changeSet = NormalizedChangeSet(
            upserts = mapOf(
                key1 to record1,
                key2 to record2
            ),
            meta = mapOf(
                key1 to createTestMeta(),
                key2 to createTestMeta()
            )
        )
        backend.apply(changeSet)

        // When
        val result = backend.read(setOf(key1, key2, key3))

        // Then
        assertEquals(3, result.size)
        assertEquals(record1, result[key1])
        assertEquals(record2, result[key2])
        assertNull(result[key3])
    }

    // ===== readOne() tests =====

    @Test
    fun readOne_givenNonExistentKey_whenRead_thenReturnsNull() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "999")

        // When
        val result = backend.readOne(key)

        // Then
        assertNull(result)
    }

    @Test
    fun readOne_givenExistingKey_whenRead_thenReturnsRecord() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")

        val changeSet = NormalizedChangeSet(
            upserts = mapOf(key to record),
            meta = mapOf(key to createTestMeta())
        )
        backend.apply(changeSet)

        // When
        val result = backend.readOne(key)

        // Then
        assertEquals(record, result)
    }

    // ===== readMeta() tests =====

    @Test
    fun readMeta_givenEmptyKeys_whenRead_thenReturnsEmptyMap() = runTest {
        // Given
        val backend = createBackend()

        // When
        val result = backend.readMeta(emptySet())

        // Then
        assertEquals(emptyMap(), result)
    }

    @Test
    fun readMeta_givenNonExistentKey_whenRead_thenReturnsNullForKey() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "999")

        // When
        val result = backend.readMeta(setOf(key))

        // Then
        assertEquals(1, result.size)
        assertNull(result[key])
    }

    @Test
    fun readMeta_givenExistingKey_whenRead_thenReturnsMeta() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")
        val meta = createTestMeta(etag = "v1", tombstone = false)

        val changeSet = NormalizedChangeSet(
            upserts = mapOf(key to record),
            meta = mapOf(key to meta)
        )
        backend.apply(changeSet)

        // When
        val result = backend.readMeta(setOf(key))

        // Then
        assertNotNull(result[key])
        assertEquals(meta, result[key])
    }

    @Test
    fun readMeta_givenMultipleKeys_whenRead_thenReturnsCorrectMeta() = runTest {
        // Given
        val backend = createBackend()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val key3 = createTestKey("User", "999")
        val meta1 = createTestMeta(etag = "v1")
        val meta2 = createTestMeta(etag = "v2")

        val changeSet = NormalizedChangeSet(
            upserts = mapOf(
                key1 to createTestRecord("name" to "Alice"),
                key2 to createTestRecord("name" to "Bob")
            ),
            meta = mapOf(
                key1 to meta1,
                key2 to meta2
            )
        )
        backend.apply(changeSet)

        // When
        val result = backend.readMeta(setOf(key1, key2, key3))

        // Then
        assertEquals(3, result.size)
        assertEquals(meta1, result[key1])
        assertEquals(meta2, result[key2])
        assertNull(result[key3])
    }

    // ===== apply() upsert tests =====

    @Test
    fun apply_givenEmptyChangeSet_whenApply_thenNoChanges() = runTest {
        // Given
        val backend = createBackend()
        val emptyChangeSet = NormalizedChangeSet()

        // When
        backend.apply(emptyChangeSet)
        val result = backend.read(emptySet())

        // Then
        assertEquals(emptyMap(), result)
    }

    @Test
    fun apply_givenUpsertWithoutMask_whenApply_thenReplacesAllFields() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")

        // Initial record with two fields
        val initialRecord = createTestRecord("name" to "Alice", "age" to 30)
        val initialChangeSet = NormalizedChangeSet(
            upserts = mapOf(key to initialRecord),
            meta = mapOf(key to createTestMeta())
        )
        backend.apply(initialChangeSet)

        // Update with only one field, no mask
        val updatedRecord = createTestRecord("name" to "Alice Updated")
        val updateChangeSet = NormalizedChangeSet(
            upserts = mapOf(key to updatedRecord),
            meta = mapOf(key to createTestMeta())
        )

        // When
        backend.apply(updateChangeSet)
        val result = backend.readOne(key)

        // Then
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(NormalizedValue.Scalar("Alice Updated"), result["name"])
    }

    @Test
    fun apply_givenUpsertWithFieldMask_whenApply_thenPatchesOnlyMaskedFields() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")

        // Initial record
        val initialRecord = createTestRecord("name" to "Alice", "age" to 30, "email" to "alice@example.com")
        val initialChangeSet = NormalizedChangeSet(
            upserts = mapOf(key to initialRecord),
            meta = mapOf(key to createTestMeta())
        )
        backend.apply(initialChangeSet)

        // Patch only name field
        val patchRecord = createTestRecord("name" to "Alice Updated", "age" to 99)
        val patchChangeSet = NormalizedChangeSet(
            upserts = mapOf(key to patchRecord),
            fieldMasks = mapOf(key to setOf("name")),
            meta = mapOf(key to createTestMeta())
        )

        // When
        backend.apply(patchChangeSet)
        val result = backend.readOne(key)

        // Then
        assertNotNull(result)
        assertEquals(NormalizedValue.Scalar("Alice Updated"), result["name"])
        assertEquals(NormalizedValue.Scalar(30), result["age"]) // Unchanged
        assertEquals(NormalizedValue.Scalar("alice@example.com"), result["email"]) // Unchanged
    }

    @Test
    fun apply_givenUpsertWithEmptyMask_whenApply_thenReplacesAllFields() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")

        val initialRecord = createTestRecord("name" to "Alice", "age" to 30)
        val initialChangeSet = NormalizedChangeSet(
            upserts = mapOf(key to initialRecord),
            meta = mapOf(key to createTestMeta())
        )
        backend.apply(initialChangeSet)

        val updatedRecord = createTestRecord("name" to "Bob")
        val updateChangeSet = NormalizedChangeSet(
            upserts = mapOf(key to updatedRecord),
            fieldMasks = mapOf(key to emptySet()),
            meta = mapOf(key to createTestMeta())
        )

        // When
        backend.apply(updateChangeSet)
        val result = backend.readOne(key)

        // Then
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(NormalizedValue.Scalar("Bob"), result["name"])
    }

    @Test
    fun apply_givenNewUpsert_whenApply_thenCreatesRecord() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")
        val changeSet = NormalizedChangeSet(
            upserts = mapOf(key to record),
            meta = mapOf(key to createTestMeta())
        )

        // When
        backend.apply(changeSet)
        val result = backend.readOne(key)

        // Then
        assertEquals(record, result)
    }

    // ===== apply() delete tests =====

    @Test
    fun apply_givenDelete_whenApply_thenRemovesRecordAndCreatesTombstone() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")

        val upsertChangeSet = NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice")),
            meta = mapOf(key to createTestMeta())
        )
        backend.apply(upsertChangeSet)

        val deleteChangeSet = NormalizedChangeSet(deletes = setOf(key))

        // When
        backend.apply(deleteChangeSet)
        val record = backend.readOne(key)
        val meta = backend.readMeta(setOf(key))[key]

        // Then
        assertNull(record)
        assertNotNull(meta)
        assertTrue(meta.tombstone)
    }

    @Test
    fun apply_givenDeleteWithoutPriorRecord_whenApply_thenCreatesTombstone() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val deleteChangeSet = NormalizedChangeSet(deletes = setOf(key))

        // When
        backend.apply(deleteChangeSet)
        val record = backend.readOne(key)
        val meta = backend.readMeta(setOf(key))[key]

        // Then
        assertNull(record)
        assertNotNull(meta)
        assertTrue(meta.tombstone)
    }

    @Test
    fun apply_givenDeleteWithDependencies_whenApply_thenCleansUpDependencyIndex() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val root = createTestRootRef()

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice")),
            meta = mapOf(key to createTestMeta())
        ))
        backend.updateRootDependencies(root, setOf(key))

        val deleteChangeSet = NormalizedChangeSet(deletes = setOf(key))

        // When
        backend.apply(deleteChangeSet)

        // Then
        val record = backend.readOne(key)
        assertNull(record)
    }

    // ===== apply() rekey tests =====

    @Test
    fun apply_givenRekey_whenApply_thenMigratesRecordToNewKey() = runTest {
        // Given
        val backend = createBackend()
        val oldKey = createTestKey("User", "temp-1")
        val newKey = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(oldKey to record),
            meta = mapOf(oldKey to createTestMeta(etag = "v1"))
        ))

        val rekeyChangeSet = NormalizedChangeSet(
            rekeys = listOf(Rekey(oldKey, newKey))
        )

        // When
        backend.apply(rekeyChangeSet)

        // Then
        val oldRecord = backend.readOne(oldKey)
        val newRecord = backend.readOne(newKey)
        val newMeta = backend.readMeta(setOf(newKey))[newKey]

        assertNull(oldRecord)
        assertEquals(record, newRecord)
        assertNotNull(newMeta)
        assertEquals("v1", newMeta.etag)
    }

    @Test
    fun apply_givenRekeyWithReferences_whenApply_thenUpdatesRefFields() = runTest {
        // Given
        val backend = createBackend()
        val oldUserKey = createTestKey("User", "temp-1")
        val newUserKey = createTestKey("User", "1")
        val postKey = createTestKey("Post", "100")

        // Create a post referencing the old user key
        val postRecord = createTestRecord("title" to "My Post", "author" to oldUserKey)
        backend.apply(NormalizedChangeSet(
            upserts = mapOf(
                oldUserKey to createTestRecord("name" to "Alice"),
                postKey to postRecord
            ),
            meta = mapOf(
                oldUserKey to createTestMeta(),
                postKey to createTestMeta()
            )
        ))

        val rekeyChangeSet = NormalizedChangeSet(
            rekeys = listOf(Rekey(oldUserKey, newUserKey))
        )

        // When
        backend.apply(rekeyChangeSet)

        // Then
        val updatedPost = backend.readOne(postKey)
        assertNotNull(updatedPost)
        val authorValue = updatedPost["author"] as? NormalizedValue.Ref
        assertNotNull(authorValue)
        assertEquals(newUserKey, authorValue.key)
    }

    @Test
    fun apply_givenRekeyWithRefList_whenApply_thenUpdatesRefListFields() = runTest {
        // Given
        val backend = createBackend()
        val oldKey1 = createTestKey("User", "temp-1")
        val newKey1 = createTestKey("User", "1")
        val oldKey2 = createTestKey("User", "temp-2")
        val groupKey = createTestKey("Group", "100")

        // Create a group referencing both users
        val groupRecord = createTestRecord(
            "name" to "Admins",
            "members" to listOf(oldKey1, oldKey2)
        )
        backend.apply(NormalizedChangeSet(
            upserts = mapOf(
                oldKey1 to createTestRecord("name" to "Alice"),
                oldKey2 to createTestRecord("name" to "Bob"),
                groupKey to groupRecord
            ),
            meta = mapOf(
                oldKey1 to createTestMeta(),
                oldKey2 to createTestMeta(),
                groupKey to createTestMeta()
            )
        ))

        val rekeyChangeSet = NormalizedChangeSet(
            rekeys = listOf(Rekey(oldKey1, newKey1))
        )

        // When
        backend.apply(rekeyChangeSet)

        // Then
        val updatedGroup = backend.readOne(groupKey)
        assertNotNull(updatedGroup)
        val membersValue = updatedGroup["members"] as? NormalizedValue.RefList
        assertNotNull(membersValue)
        assertEquals(listOf(newKey1, oldKey2), membersValue.keys)
    }

    @Test
    fun apply_givenRekeyWithDependencies_whenApply_thenUpdatesDependencyIndex() = runTest {
        // Given
        val backend = createBackend()
        val oldKey = createTestKey("User", "temp-1")
        val newKey = createTestKey("User", "1")
        val root = createTestRootRef()

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(oldKey to createTestRecord("name" to "Alice")),
            meta = mapOf(oldKey to createTestMeta())
        ))
        backend.updateRootDependencies(root, setOf(oldKey))

        val rekeyChangeSet = NormalizedChangeSet(
            rekeys = listOf(Rekey(oldKey, newKey))
        )

        // When
        backend.apply(rekeyChangeSet)

        // Then
        val newRecord = backend.readOne(newKey)
        assertNotNull(newRecord)
    }

    // ===== apply() with entity and root invalidations =====

    @Test
    fun apply_givenUpsert_whenApply_thenEmitsEntityInvalidation() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val record = createTestRecord("name" to "Alice")
        val changeSet = NormalizedChangeSet(
            upserts = mapOf(key to record),
            meta = mapOf(key to createTestMeta())
        )

        // When
        backend.entityInvalidations.test {
            backend.apply(changeSet)

            // Then
            val invalidations = awaitItem()
            assertEquals(setOf(key), invalidations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun apply_givenDelete_whenApply_thenEmitsEntityInvalidation() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice")),
            meta = mapOf(key to createTestMeta())
        ))

        val deleteChangeSet = NormalizedChangeSet(deletes = setOf(key))

        // When
        backend.entityInvalidations.test {
            backend.apply(deleteChangeSet)

            // Then
            val invalidations = awaitItem()
            assertEquals(setOf(key), invalidations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun apply_givenRekey_whenApply_thenEmitsBothKeysInEntityInvalidation() = runTest {
        // Given
        val backend = createBackend()
        val oldKey = createTestKey("User", "temp-1")
        val newKey = createTestKey("User", "1")

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(oldKey to createTestRecord("name" to "Alice")),
            meta = mapOf(oldKey to createTestMeta())
        ))

        val rekeyChangeSet = NormalizedChangeSet(
            rekeys = listOf(Rekey(oldKey, newKey))
        )

        // When
        backend.entityInvalidations.test {
            backend.apply(rekeyChangeSet)

            // Then
            val invalidations = awaitItem()
            assertTrue(invalidations.contains(oldKey))
            assertTrue(invalidations.contains(newKey))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun apply_givenChangeWithDependentRoot_whenApply_thenEmitsRootInvalidation() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val root = createTestRootRef()

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice")),
            meta = mapOf(key to createTestMeta())
        ))
        backend.updateRootDependencies(root, setOf(key))

        val updateChangeSet = NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice Updated")),
            meta = mapOf(key to createTestMeta())
        )

        // When
        backend.rootInvalidations.test {
            backend.apply(updateChangeSet)

            // Then
            val invalidations = awaitItem()
            assertEquals(setOf(root), invalidations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun apply_givenComplexChangeSet_whenApply_thenAppliesAllOperationsCorrectly() = runTest {
        // Given
        val backend = createBackend()
        val upsertKey = createTestKey("User", "1")
        val deleteKey = createTestKey("User", "2")
        val oldKey = createTestKey("User", "temp-3")
        val newKey = createTestKey("User", "3")

        // Setup initial state
        backend.apply(NormalizedChangeSet(
            upserts = mapOf(
                deleteKey to createTestRecord("name" to "Bob"),
                oldKey to createTestRecord("name" to "Charlie")
            ),
            meta = mapOf(
                deleteKey to createTestMeta(),
                oldKey to createTestMeta()
            )
        ))

        val complexChangeSet = NormalizedChangeSet(
            upserts = mapOf(upsertKey to createTestRecord("name" to "Alice")),
            deletes = setOf(deleteKey),
            rekeys = listOf(Rekey(oldKey, newKey)),
            meta = mapOf(upsertKey to createTestMeta())
        )

        // When
        backend.apply(complexChangeSet)

        // Then
        val upsertedRecord = backend.readOne(upsertKey)
        val deletedRecord = backend.readOne(deleteKey)
        val oldRecord = backend.readOne(oldKey)
        val rekeyedRecord = backend.readOne(newKey)

        assertNotNull(upsertedRecord)
        assertNull(deletedRecord)
        assertNull(oldRecord)
        assertNotNull(rekeyedRecord)
    }

    // ===== updateRootDependencies() tests =====

    @Test
    fun updateRootDependencies_givenNewRoot_whenUpdate_thenCreatesDependencies() = runTest {
        // Given
        val backend = createBackend()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val root = createTestRootRef()

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(
                key1 to createTestRecord("name" to "Alice"),
                key2 to createTestRecord("name" to "Bob")
            ),
            meta = mapOf(
                key1 to createTestMeta(),
                key2 to createTestMeta()
            )
        ))

        // When
        backend.updateRootDependencies(root, setOf(key1, key2))

        // Then
        // Verify by updating an entity and checking root invalidation
        backend.rootInvalidations.test {
            backend.apply(NormalizedChangeSet(
                upserts = mapOf(key1 to createTestRecord("name" to "Alice Updated")),
                meta = mapOf(key1 to createTestMeta())
            ))

            val invalidations = awaitItem()
            assertEquals(setOf(root), invalidations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateRootDependencies_givenExistingRoot_whenUpdate_thenUpdatesDepencies() = runTest {
        // Given
        val backend = createBackend()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val key3 = createTestKey("User", "3")
        val root = createTestRootRef()

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(
                key1 to createTestRecord("name" to "Alice"),
                key2 to createTestRecord("name" to "Bob"),
                key3 to createTestRecord("name" to "Charlie")
            ),
            meta = mapOf(
                key1 to createTestMeta(),
                key2 to createTestMeta(),
                key3 to createTestMeta()
            )
        ))

        // Initial dependencies
        backend.updateRootDependencies(root, setOf(key1, key2))

        // When - update to different dependencies
        backend.updateRootDependencies(root, setOf(key2, key3))

        // Then - key1 should no longer trigger root invalidation
        backend.rootInvalidations.test {
            backend.apply(NormalizedChangeSet(
                upserts = mapOf(key1 to createTestRecord("name" to "Alice Updated")),
                meta = mapOf(key1 to createTestMeta())
            ))

            // No root invalidation should occur for key1
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateRootDependencies_givenEmptyDependencies_whenUpdate_thenRemovesDependencies() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val root = createTestRootRef()

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice")),
            meta = mapOf(key to createTestMeta())
        ))
        backend.updateRootDependencies(root, setOf(key))

        // When
        backend.updateRootDependencies(root, emptySet())

        // Then
        backend.rootInvalidations.test {
            backend.apply(NormalizedChangeSet(
                upserts = mapOf(key to createTestRecord("name" to "Alice Updated")),
                meta = mapOf(key to createTestMeta())
            ))

            // No root invalidation should occur
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateRootDependencies_givenSameDependencies_whenUpdate_thenDoesNotEmitSignal() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val root = createTestRootRef()

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice")),
            meta = mapOf(key to createTestMeta())
        ))
        backend.updateRootDependencies(root, setOf(key))

        // When
        backend.rootInvalidations.test {
            backend.updateRootDependencies(root, setOf(key))

            // Then - no signal should be emitted
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateRootDependencies_givenDifferentDependencies_whenUpdate_thenEmitsRootSignal() = runTest {
        // Given
        val backend = createBackend()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val root = createTestRootRef()

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(
                key1 to createTestRecord("name" to "Alice"),
                key2 to createTestRecord("name" to "Bob")
            ),
            meta = mapOf(
                key1 to createTestMeta(),
                key2 to createTestMeta()
            )
        ))
        backend.updateRootDependencies(root, setOf(key1))

        // When
        backend.rootInvalidations.test {
            backend.updateRootDependencies(root, setOf(key2))

            // Then
            val invalidations = awaitItem()
            assertEquals(setOf(root), invalidations)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ===== Concurrent access tests =====

    @Test
    fun apply_givenConcurrentReads_whenApply_thenThreadSafe() = runTest {
        // Given
        val backend = createBackend()
        val keys = (1..100).map { createTestKey("User", it.toString()) }
        val records = keys.map { it to createTestRecord("name" to "User ${it.id}") }

        backend.apply(NormalizedChangeSet(
            upserts = records.toMap(),
            meta = keys.associateWith { createTestMeta() }
        ))

        // When - concurrent reads and writes
        val operations = (1..50).map { idx ->
            async {
                if (idx % 2 == 0) {
                    backend.read(setOf(keys[idx % keys.size]))
                } else {
                    backend.apply(NormalizedChangeSet(
                        upserts = mapOf(keys[idx % keys.size] to createTestRecord("name" to "Updated $idx")),
                        meta = mapOf(keys[idx % keys.size] to createTestMeta())
                    ))
                }
            }
        }

        // Then - all operations complete without exception
        operations.awaitAll()
        val finalRecords = backend.read(keys.toSet())
        assertEquals(keys.size, finalRecords.size)
    }

    @Test
    fun updateRootDependencies_givenConcurrentUpdates_whenUpdate_thenThreadSafe() = runTest {
        // Given
        val backend = createBackend()
        val keys = (1..10).map { createTestKey("User", it.toString()) }
        val roots = (1..10).map { createTestRootRef(
            requestKey = QueryKey(StoreNamespace("test"), mapOf("query" to "query$it")),
            shapeId = "shape$it"
        ) }

        backend.apply(NormalizedChangeSet(
            upserts = keys.associateWith { createTestRecord("name" to "User ${it.id}") },
            meta = keys.associateWith { createTestMeta() }
        ))

        // When - concurrent dependency updates
        val operations = roots.map { root ->
            async {
                backend.updateRootDependencies(root, keys.take(5).toSet())
            }
        }

        // Then - all operations complete without exception
        operations.awaitAll()
    }

    // ===== Edge case tests =====

    @Test
    fun apply_givenMultipleRekeysInSequence_whenApply_thenAppliesInOrder() = runTest {
        // Given
        val backend = createBackend()
        val key1 = createTestKey("User", "1")
        val key2 = createTestKey("User", "2")
        val key3 = createTestKey("User", "3")

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(key1 to createTestRecord("name" to "Alice")),
            meta = mapOf(key1 to createTestMeta())
        ))

        val rekeyChangeSet = NormalizedChangeSet(
            rekeys = listOf(
                Rekey(key1, key2),
                Rekey(key2, key3)
            )
        )

        // When
        backend.apply(rekeyChangeSet)

        // Then
        assertNull(backend.readOne(key1))
        assertNull(backend.readOne(key2))
        assertNotNull(backend.readOne(key3))
    }

    @Test
    fun apply_givenUpsertAndDeleteSameKey_whenApply_thenDeleteTakesPrecedence() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")

        backend.apply(NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice")),
            meta = mapOf(key to createTestMeta())
        ))

        val changeSet = NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Bob")),
            deletes = setOf(key),
            meta = mapOf(key to createTestMeta())
        )

        // When
        backend.apply(changeSet)

        // Then
        val record = backend.readOne(key)
        val meta = backend.readMeta(setOf(key))[key]
        assertNull(record)
        assertNotNull(meta)
        assertTrue(meta.tombstone)
    }

    @Test
    fun apply_givenRekeyNonExistentKey_whenApply_thenNoOp() = runTest {
        // Given
        val backend = createBackend()
        val oldKey = createTestKey("User", "999")
        val newKey = createTestKey("User", "1000")

        val rekeyChangeSet = NormalizedChangeSet(
            rekeys = listOf(Rekey(oldKey, newKey))
        )

        // When
        backend.apply(rekeyChangeSet)

        // Then
        assertNull(backend.readOne(oldKey))
        assertNull(backend.readOne(newKey))
    }

    @Test
    fun entityInvalidations_givenMultipleSubscribers_whenApply_thenAllReceiveEvents() = runTest {
        // Given
        val backend = createBackend()
        val key = createTestKey("User", "1")
        val changeSet = NormalizedChangeSet(
            upserts = mapOf(key to createTestRecord("name" to "Alice")),
            meta = mapOf(key to createTestMeta())
        )

        // When
        val subscriber1Job = async {
            backend.entityInvalidations.test {
                backend.apply(changeSet)
                val invalidations = awaitItem()
                assertEquals(setOf(key), invalidations)
                cancelAndIgnoreRemainingEvents()
            }
        }

        val subscriber2Job = async {
            backend.entityInvalidations.test {
                backend.apply(changeSet)
                val invalidations = awaitItem()
                assertEquals(setOf(key), invalidations)
                cancelAndIgnoreRemainingEvents()
            }
        }

        // Then
        subscriber1Job.await()
        subscriber2Job.await()
    }
}
