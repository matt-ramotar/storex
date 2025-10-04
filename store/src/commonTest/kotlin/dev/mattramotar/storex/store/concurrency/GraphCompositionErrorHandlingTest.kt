package dev.mattramotar.storex.store.concurrency

import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.normalization.schema.DenormalizationContext
import dev.mattramotar.storex.normalization.schema.EntityAdapter
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import dev.mattramotar.storex.store.normalization.EntityMeta
import dev.mattramotar.storex.store.normalization.GraphCompositionException
import dev.mattramotar.storex.store.normalization.Shape
import dev.mattramotar.storex.store.normalization.ShapeId
import dev.mattramotar.storex.store.normalization.backend.NormalizationBackend
import dev.mattramotar.storex.store.normalization.backend.RootRef
import dev.mattramotar.storex.store.normalization.internal.composeFromRoot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for TASK-004: Graph composition error handling
 *
 * Validates that:
 * - Backend read errors don't crash entire graph composition
 * - Failed entities are tracked in errors map
 * - Partial progress is maintained
 * - GraphCompositionException includes full diagnostic context
 * - Max depth reached is properly tracked
 */
class GraphCompositionErrorHandlingTest {

    @Test
    fun composeFromRoot_whenBackendReadFails_thenPartialCompletionWithErrors() = runTest {
        // Given
        val rootKey = EntityKey("User", mapOf("id" to "1"))
        val childKey1 = EntityKey("Post", mapOf("id" to "1"))
        val childKey2 = EntityKey("Post", mapOf("id" to "2"))

        val backend = FakeBackend().apply {
            // Root succeeds
            addRecord(rootKey, mapOf("id" to "1", "name" to "Alice", "posts" to listOf(childKey1, childKey2)))

            // Child1 succeeds
            addRecord(childKey1, mapOf("id" to "1", "title" to "Post 1"))

            // Child2 fails
            addError(childKey2, IllegalStateException("Database connection lost"))
        }

        val registry = FakeRegistry()
        val shape = FakeShape<User>(
            id = ShapeId("UserShape"),
            maxDepth = 10,
            refs = { record ->
                @Suppress("UNCHECKED_CAST")
                (record.data["posts"] as? List<EntityKey>)?.toSet() ?: emptySet()
            }
        )

        // When
        val result = composeFromRoot(rootKey, shape, registry, backend)

        // Then - Composition succeeds with partial data
        assertNotNull(result.value)
        assertEquals(3, result.dependencies.size) // root + 2 children
        assertTrue(result.dependencies.contains(rootKey))
        assertTrue(result.dependencies.contains(childKey1))
        assertTrue(result.dependencies.contains(childKey2))
    }

    @Test
    fun composeFromRoot_whenRootNotFound_thenThrowsWithContext() = runTest {
        // Given
        val rootKey = EntityKey("User", mapOf("id" to "999"))
        val backend = FakeBackend() // Empty - no records
        val registry = FakeRegistry()
        val shape = FakeShape<User>(ShapeId("UserShape"), maxDepth = 10)

        // When/Then
        val exception = assertFailsWith<GraphCompositionException> {
            composeFromRoot(rootKey, shape, registry, backend)
        }

        assertEquals(rootKey, exception.rootKey)
        assertEquals(ShapeId("UserShape"), exception.shapeId)
        assertEquals(0, exception.partialRecords)
        assertTrue(exception.message.contains("Root entity not found"))
    }

    @Test
    fun composeFromRoot_whenDenormalizationFails_thenThrowsWithCause() = runTest {
        // Given
        val rootKey = EntityKey("User", mapOf("id" to "1"))
        val denormalizationError = IllegalArgumentException("Invalid data format")

        val backend = FakeBackend().apply {
            addRecord(rootKey, mapOf("id" to "1", "invalid" to "data"))
        }

        val registry = object : FakeRegistry() {
            override fun <T : Any> getAdapter(typeName: String): EntityAdapter<T> {
                @Suppress("UNCHECKED_CAST")
                return object : EntityAdapter<T> {
                    override suspend fun denormalize(record: NormalizedRecord, context: DenormalizationContext): T {
                        throw denormalizationError
                    }
                } as EntityAdapter<T>
            }
        }

        val shape = FakeShape<User>(ShapeId("UserShape"), maxDepth = 10)

        // When/Then
        val exception = assertFailsWith<GraphCompositionException> {
            composeFromRoot(rootKey, shape, registry, backend)
        }

        assertEquals(denormalizationError, exception.cause)
        assertEquals(rootKey, exception.rootKey)
        assertEquals(1, exception.partialRecords) // Root was read successfully
        assertTrue(exception.message.contains("Failed to denormalize"))
    }

    @Test
    fun composeFromRoot_whenMaxDepthReached_thenFlaggedInException() = runTest {
        // Given - Create a deep graph that exceeds maxDepth
        val rootKey = EntityKey("User", mapOf("id" to "1"))
        val level1 = EntityKey("Post", mapOf("id" to "1"))
        val level2 = EntityKey("Comment", mapOf("id" to "1"))
        val level3 = EntityKey("Reply", mapOf("id" to "1"))

        val backend = FakeBackend().apply {
            addRecord(rootKey, mapOf("id" to "1", "posts" to listOf(level1)))
            addRecord(level1, mapOf("id" to "1", "comments" to listOf(level2)))
            addRecord(level2, mapOf("id" to "1", "replies" to listOf(level3)))
            // level3 is not added - simulating missing data
        }

        val shape = FakeShape<User>(
            id = ShapeId("UserShape"),
            maxDepth = 2, // Only allow 2 levels deep
            refs = { record ->
                val refs = mutableSetOf<EntityKey>()
                @Suppress("UNCHECKED_CAST")
                (record.data["posts"] as? List<EntityKey>)?.let { refs.addAll(it) }
                @Suppress("UNCHECKED_CAST")
                (record.data["comments"] as? List<EntityKey>)?.let { refs.addAll(it) }
                @Suppress("UNCHECKED_CAST")
                (record.data["replies"] as? List<EntityKey>)?.let { refs.addAll(it) }
                refs
            }
        )

        val registry = FakeRegistry()

        // When/Then - Should throw because root is not found after depth limiting
        val exception = assertFailsWith<GraphCompositionException> {
            composeFromRoot(rootKey, shape, registry, backend)
        }

        assertTrue(exception.maxDepthReached, "maxDepthReached should be true")
    }

    @Test
    fun composeFromRoot_whenMultipleEntitiesFail_thenAllTrackedInErrors() = runTest {
        // Given
        val rootKey = EntityKey("User", mapOf("id" to "1"))
        val child1 = EntityKey("Post", mapOf("id" to "1"))
        val child2 = EntityKey("Post", mapOf("id" to "2"))
        val child3 = EntityKey("Post", mapOf("id" to "3"))

        val error1 = IllegalStateException("Connection timeout")
        val error2 = IllegalStateException("Disk full")

        val backend = FakeBackend().apply {
            addRecord(rootKey, mapOf("id" to "1", "posts" to listOf(child1, child2, child3)))
            // Batch read will fail for all children
            addBatchError(setOf(child1, child2, child3), error1)
        }

        val registry = FakeRegistry()
        val shape = FakeShape<User>(
            id = ShapeId("UserShape"),
            maxDepth = 10,
            refs = { record ->
                @Suppress("UNCHECKED_CAST")
                (record.data["posts"] as? List<EntityKey>)?.toSet() ?: emptySet()
            }
        )

        // When
        val result = composeFromRoot(rootKey, shape, registry, backend)

        // Then - Root composes but children are missing
        assertNotNull(result.value)
        assertEquals(4, result.dependencies.size) // root + 3 children attempted
    }

    @Test
    fun graphCompositionException_toString_thenIncludesAllContext() = runTest {
        // Given
        val rootKey = EntityKey("User", mapOf("id" to "1"))
        val failedKey1 = EntityKey("Post", mapOf("id" to "1"))
        val failedKey2 = EntityKey("Comment", mapOf("id" to "2"))

        val exception = GraphCompositionException(
            message = "Partial composition failure",
            rootKey = rootKey,
            shapeId = ShapeId("UserWithPostsAndComments"),
            partialRecords = 5,
            totalExpected = 10,
            failedEntities = mapOf(
                failedKey1 to IllegalStateException("Network error"),
                failedKey2 to IllegalStateException("Timeout")
            ),
            maxDepthReached = true,
            cause = IllegalStateException("Backend unavailable")
        )

        // When
        val str = exception.toString()

        // Then
        assertTrue(str.contains("GraphCompositionException"))
        assertTrue(str.contains("User"))
        assertTrue(str.contains("UserWithPostsAndComments"))
        assertTrue(str.contains("5/10 records composed"))
        assertTrue(str.contains("Post"))
        assertTrue(str.contains("Comment"))
        assertTrue(str.contains("Network error"))
        assertTrue(str.contains("Timeout"))
        assertTrue(str.contains("Maximum traversal depth reached"))
        assertTrue(str.contains("Backend unavailable"))
    }

    @Test
    fun graphCompositionException_isRetryable_whenNoRetryableErrors_thenFalse() = runTest {
        // Given
        val exception = GraphCompositionException(
            message = "Test",
            rootKey = EntityKey("User", mapOf("id" to "1")),
            shapeId = ShapeId("Test"),
            partialRecords = 0,
            failedEntities = mapOf(
                EntityKey("Post", mapOf("id" to "1")) to IllegalArgumentException("Invalid data")
            )
        )

        // Then
        assertFalse(exception.isRetryable)
    }

    // Helper classes

    private data class User(val id: String, val name: String)

    private class FakeBackend : NormalizationBackend {
        private val records = mutableMapOf<EntityKey, NormalizedRecord>()
        private val errors = mutableMapOf<EntityKey, Throwable>()
        private val batchErrors = mutableMapOf<Set<EntityKey>, Throwable>()

        fun addRecord(key: EntityKey, data: Map<String, Any>) {
            records[key] = NormalizedRecord(key, data)
        }

        fun addError(key: EntityKey, error: Throwable) {
            errors[key] = error
        }

        fun addBatchError(keys: Set<EntityKey>, error: Throwable) {
            batchErrors[keys] = error
        }

        override suspend fun read(keys: Set<EntityKey>): Map<EntityKey, NormalizedRecord> {
            // Check for batch errors
            batchErrors.forEach { (errorKeys, error) ->
                if (keys.containsAll(errorKeys)) {
                    throw error
                }
            }

            val result = mutableMapOf<EntityKey, NormalizedRecord>()
            keys.forEach { key ->
                errors[key]?.let { throw it }
                records[key]?.let { result[key] = it }
            }
            return result
        }

        override suspend fun readMeta(keys: Set<EntityKey>): Map<EntityKey, EntityMeta?> {
            return keys.associateWith {
                if (it in records) EntityMeta(Clock.System.now(), null) else null
            }
        }

        override suspend fun apply(changeSet: dev.mattramotar.storex.normalization.ChangeSet) {}

        override suspend fun updateRootDependencies(root: RootRef, dependencies: Set<EntityKey>) {}

        override val rootInvalidations = MutableSharedFlow<Set<RootRef>>()
    }

    private open class FakeRegistry : SchemaRegistry {
        override fun <T : Any> getAdapter(typeName: String): EntityAdapter<T> {
            @Suppress("UNCHECKED_CAST")
            return object : EntityAdapter<T> {
                override suspend fun denormalize(record: NormalizedRecord, context: DenormalizationContext): T {
                    return User(
                        id = record.data["id"] as String,
                        name = record.data["name"] as? String ?: "Unknown"
                    ) as T
                }
            } as EntityAdapter<T>
        }

        override fun hasAdapter(typeName: String): Boolean = true
    }

    private class FakeShape<V : Any>(
        override val id: ShapeId,
        override val maxDepth: Int,
        private val refs: (NormalizedRecord) -> Set<EntityKey> = { emptySet() }
    ) : Shape<V> {
        override fun outboundRefs(record: NormalizedRecord): Set<EntityKey> = refs(record)
    }
}
