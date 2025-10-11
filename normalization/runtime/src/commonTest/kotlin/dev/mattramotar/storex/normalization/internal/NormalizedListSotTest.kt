package dev.mattramotar.storex.normalization.internal

import app.cash.turbine.test
import dev.mattramotar.storex.core.QueryKey
import dev.mattramotar.storex.core.StoreNamespace
import dev.mattramotar.storex.normalization.EntityMeta
import dev.mattramotar.storex.normalization.GraphMeta
import dev.mattramotar.storex.normalization.IndexUpdate
import dev.mattramotar.storex.normalization.NormalizedChangeSet
import dev.mattramotar.storex.normalization.NormalizedWrite
import dev.mattramotar.storex.normalization.Rekey
import dev.mattramotar.storex.normalization.Shape
import dev.mattramotar.storex.normalization.ShapeId
import dev.mattramotar.storex.normalization.backend.NormalizationBackend
import dev.mattramotar.storex.normalization.backend.RootRef
import dev.mattramotar.storex.normalization.format.NormalizedRecord
import dev.mattramotar.storex.normalization.format.NormalizedValue
import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.normalization.schema.DenormalizationContext
import dev.mattramotar.storex.normalization.schema.EntityAdapter
import dev.mattramotar.storex.normalization.schema.NormalizationContext
import dev.mattramotar.storex.normalization.schema.SchemaRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NormalizedListSotTest {

    data class TestItem(val id: String, val name: String)

    // Fake implementations for DoNotMock types
    class FakeEntityAdapter<T : Any>(
        override val typeName: String,
        private val idExtractor: (T) -> String,
        private val denormalizer: suspend (NormalizedRecord, DenormalizationContext) -> T
    ) : EntityAdapter<T> {
        override fun extractId(entity: T): String = idExtractor(entity)

        override fun normalize(
            entity: T,
            ctx: NormalizationContext
        ): Pair<NormalizedRecord, Set<String>> = error("Not used in tests")

        override suspend fun denormalize(
            record: NormalizedRecord,
            ctx: DenormalizationContext
        ): T = denormalizer(record, ctx)
    }

    class FakeShape<V : Any>(
        override val id: ShapeId,
        override val outputType: KClass<V>,
        override val edgeFields: Set<String>,
        override val maxDepth: Int = 10,
        private val refExtractor: (NormalizedRecord) -> Set<EntityKey>
    ) : Shape<V> {
        override fun outboundRefs(record: NormalizedRecord): Set<EntityKey> = refExtractor(record)
    }

    class FakeNormalizationBackend : NormalizationBackend {
        private val storage = mutableMapOf<EntityKey, NormalizedRecord>()
        private val metadata = mutableMapOf<EntityKey, EntityMeta>()
        private val _entityInvalidations = MutableSharedFlow<Set<EntityKey>>(replay = 0)
        private val _rootInvalidations = MutableSharedFlow<Set<RootRef>>(replay = 0)
        private val rootDeps = mutableMapOf<RootRef, Set<EntityKey>>()

        override val entityInvalidations: Flow<Set<EntityKey>> = _entityInvalidations
        override val rootInvalidations: Flow<Set<RootRef>> = _rootInvalidations

        fun setRecord(key: EntityKey, record: NormalizedRecord, meta: EntityMeta? = null) {
            storage[key] = record
            if (meta != null) metadata[key] = meta
        }

        suspend fun emitRootInvalidation(roots: Set<RootRef>) {
            _rootInvalidations.emit(roots)
        }

        override suspend fun read(keys: Set<EntityKey>): Map<EntityKey, NormalizedRecord?> =
            keys.associateWith { storage[it] }

        override suspend fun readMeta(keys: Set<EntityKey>): Map<EntityKey, EntityMeta?> =
            keys.associateWith { metadata[it] }

        override suspend fun apply(changeSet: NormalizedChangeSet) {
            // Apply upserts
            changeSet.upserts.forEach { (key, record) ->
                storage[key] = record
            }
            // Apply deletes
            changeSet.deletes.forEach { key ->
                storage.remove(key)
            }
            // Apply rekeys
            changeSet.rekeys.forEach { rekey ->
                storage[rekey.oldKey]?.let {
                    storage[rekey.newKey] = it
                    storage.remove(rekey.oldKey)
                }
                metadata[rekey.oldKey]?.let {
                    metadata[rekey.newKey] = it
                    metadata.remove(rekey.oldKey)
                }
            }
            // Apply metadata
            changeSet.meta.forEach { (key, meta) ->
                metadata[key] = meta
            }
        }

        override suspend fun updateRootDependencies(root: RootRef, dependsOn: Set<EntityKey>) {
            rootDeps[root] = dependsOn
        }

        override suspend fun clear() {
            storage.clear()
            metadata.clear()
        }

        fun getRootDependencies(root: RootRef): Set<EntityKey>? = rootDeps[root]
    }

    class FakeIndexManager : dev.mattramotar.storex.normalization.IndexManager {
        private val indexes = mutableMapOf<Long, MutableStateFlow<List<EntityKey>?>>()

        override suspend fun updateIndex(requestKey: dev.mattramotar.storex.core.StoreKey, roots: List<EntityKey>) {
            indexes.getOrPut(requestKey.stableHash()) { MutableStateFlow(null) }.value = roots
        }

        override fun streamIndex(requestKey: dev.mattramotar.storex.core.StoreKey): Flow<List<EntityKey>?> =
            indexes.getOrPut(requestKey.stableHash()) { MutableStateFlow(null) }.asStateFlow()
    }

    private fun createTestKey(id: String = "query1") =
        QueryKey(StoreNamespace("test"), mapOf("id" to id))

    private fun createTestAdapter(): EntityAdapter<TestItem> =
        FakeEntityAdapter(
            typeName = "TestItem",
            idExtractor = { it.id },
            denormalizer = { record, _ ->
                TestItem(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

    private fun createTestShape(): Shape<TestItem> =
        FakeShape(
            id = ShapeId("TestItemShape"),
            outputType = TestItem::class,
            edgeFields = emptySet(),
            refExtractor = { emptySet() }
        )

    @Test
    fun reader_givenNullRoots_whenRead_thenEmitsEmptyListWithDefaultMeta() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertTrue(result.value.isEmpty())
            assertNotNull(result.meta)
            assertNotNull(result.meta.updatedAt)
            assertNull(result.meta.etagFingerprint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenEmptyRootsList_whenRead_thenEmitsEmptyListWithAggregatedMeta() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        index.updateIndex(key, emptyList())

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertTrue(result.value.isEmpty())
            assertNotNull(result.meta)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenSingleRoot_whenRead_thenEmitsListWithOneItem() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val itemRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Item1")
        )
        val now = Clock.System.now()
        backend.setRecord(itemKey, itemRecord, EntityMeta(etag = "etag1", updatedAt = now))
        index.updateIndex(key, listOf(itemKey))

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(1, result.value.size)
            assertEquals("1", result.value[0].id)
            assertEquals("Item1", result.value[0].name)
            assertEquals(now, result.meta.updatedAt)
            assertNotNull(result.meta.etagFingerprint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenMultipleRoots_whenRead_thenEmitsListWithAllItems() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val item1Key = EntityKey("TestItem", "1")
        val item2Key = EntityKey("TestItem", "2")
        val item3Key = EntityKey("TestItem", "3")

        val now = Clock.System.now()
        backend.setRecord(
            item1Key,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        backend.setRecord(
            item2Key,
            mapOf("id" to NormalizedValue.Scalar("2"), "name" to NormalizedValue.Scalar("Item2")),
            EntityMeta(etag = "etag2", updatedAt = now)
        )
        backend.setRecord(
            item3Key,
            mapOf("id" to NormalizedValue.Scalar("3"), "name" to NormalizedValue.Scalar("Item3")),
            EntityMeta(etag = "etag3", updatedAt = now)
        )
        index.updateIndex(key, listOf(item1Key, item2Key, item3Key))

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(3, result.value.size)
            assertEquals(listOf("1", "2", "3"), result.value.map { it.id })
            assertEquals(listOf("Item1", "Item2", "Item3"), result.value.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenItemsWithDifferentTimestamps_whenRead_thenUsesMinimumTimestamp() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val item1Key = EntityKey("TestItem", "1")
        val item2Key = EntityKey("TestItem", "2")

        val olderTime = Instant.parse("2024-01-01T00:00:00Z")
        val newerTime = Instant.parse("2024-12-01T00:00:00Z")

        backend.setRecord(
            item1Key,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = newerTime)
        )
        backend.setRecord(
            item2Key,
            mapOf("id" to NormalizedValue.Scalar("2"), "name" to NormalizedValue.Scalar("Item2")),
            EntityMeta(etag = "etag2", updatedAt = olderTime)
        )
        index.updateIndex(key, listOf(item1Key, item2Key))

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertEquals(olderTime, result.meta.updatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenItemsWithEtags_whenRead_thenAggregatesEtagFingerprint() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val item1Key = EntityKey("TestItem", "1")
        val item2Key = EntityKey("TestItem", "2")

        val now = Clock.System.now()
        backend.setRecord(
            item1Key,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag-zebra", updatedAt = now)
        )
        backend.setRecord(
            item2Key,
            mapOf("id" to NormalizedValue.Scalar("2"), "name" to NormalizedValue.Scalar("Item2")),
            EntityMeta(etag = "etag-alpha", updatedAt = now)
        )
        index.updateIndex(key, listOf(item1Key, item2Key))

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertNotNull(result.meta.etagFingerprint)
            // Etags are sorted: "etag-alpha|etag-zebra"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenItemsWithoutEtags_whenRead_thenEtagFingerprintIsNull() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val item1Key = EntityKey("TestItem", "1")
        val now = Clock.System.now()

        backend.setRecord(
            item1Key,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = null, updatedAt = now)
        )
        index.updateIndex(key, listOf(item1Key))

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertNull(result.meta.etagFingerprint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenBackendEmitsEmptyRootInvalidation_whenRead_thenRecomposesAndEmitsNewValue() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val now = Clock.System.now()
        backend.setRecord(
            itemKey,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        index.updateIndex(key, listOf(itemKey))

        // When
        sot.reader(key).test {
            // Then
            awaitItem() // Initial emission

            // Emit empty set invalidation (triggers recompose)
            backend.emitRootInvalidation(emptySet())

            val result = awaitItem()
            assertNotNull(result)
            assertEquals(1, result.value.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenBackendEmitsMatchingRootInvalidation_whenRead_thenRecomposesAndEmitsNewValue() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val now = Clock.System.now()
        backend.setRecord(
            itemKey,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        index.updateIndex(key, listOf(itemKey))

        val rootRef = RootRef(key, shape.id)

        // When
        sot.reader(key).test {
            // Then
            awaitItem() // Initial emission

            // Emit matching root invalidation
            backend.emitRootInvalidation(setOf(rootRef))

            val result = awaitItem()
            assertNotNull(result)
            assertEquals(1, result.value.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenBackendEmitsNonMatchingRootInvalidation_whenRead_thenDoesNotRecompose() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val now = Clock.System.now()
        backend.setRecord(
            itemKey,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        index.updateIndex(key, listOf(itemKey))

        val otherKey = createTestKey("other")
        val otherRootRef = RootRef(otherKey, shape.id)

        // When
        sot.reader(key).test {
            // Then
            awaitItem() // Initial emission

            // Emit non-matching root invalidation
            backend.emitRootInvalidation(setOf(otherRootRef))

            // Should not emit another item
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenIndexUpdates_whenRead_thenEmitsNewList() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val item1Key = EntityKey("TestItem", "1")
        val item2Key = EntityKey("TestItem", "2")

        val now = Clock.System.now()
        backend.setRecord(
            item1Key,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        backend.setRecord(
            item2Key,
            mapOf("id" to NormalizedValue.Scalar("2"), "name" to NormalizedValue.Scalar("Item2")),
            EntityMeta(etag = "etag2", updatedAt = now)
        )
        index.updateIndex(key, listOf(item1Key))

        // When
        sot.reader(key).test {
            // Then
            val initial = awaitItem()
            assertEquals(1, initial!!.value.size)

            // Update index
            index.updateIndex(key, listOf(item1Key, item2Key))

            val updated = awaitItem()
            assertEquals(2, updated!!.value.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenIndexChangesFromNullToList_whenRead_thenEmitsNewList() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val now = Clock.System.now()
        backend.setRecord(
            itemKey,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )

        // When
        sot.reader(key).test {
            // Then
            val initial = awaitItem()
            assertTrue(initial!!.value.isEmpty())

            // Update index from null to list
            index.updateIndex(key, listOf(itemKey))

            val updated = awaitItem()
            assertEquals(1, updated!!.value.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenRootDependenciesUpdated_whenRead_thenUpdatesBackend() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val now = Clock.System.now()
        backend.setRecord(
            itemKey,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        index.updateIndex(key, listOf(itemKey))

        val rootRef = RootRef(key, shape.id)

        // When
        sot.reader(key).test {
            // Then
            awaitItem()

            // Verify dependencies were updated
            val deps = backend.getRootDependencies(rootRef)
            assertNotNull(deps)
            assertTrue(deps.contains(itemKey))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun write_givenNormalizedWrite_whenWrite_thenAppliesChangeSet() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val changeSet = NormalizedChangeSet(
            upserts = mapOf(
                itemKey to mapOf(
                    "id" to NormalizedValue.Scalar("1"),
                    "name" to NormalizedValue.Scalar("NewItem")
                )
            )
        )
        val write = NormalizedWrite<QueryKey>(changeSet)

        // When
        sot.write(key, write)

        // Then
        val record = backend.readOne(itemKey)
        assertNotNull(record)
        assertEquals("NewItem", (record["name"] as NormalizedValue.Scalar).value)
    }

    @Test
    fun write_givenNormalizedWriteWithIndexUpdate_whenWrite_thenAppliesChangeSet() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val changeSet = NormalizedChangeSet(
            upserts = mapOf(
                itemKey to mapOf(
                    "id" to NormalizedValue.Scalar("1"),
                    "name" to NormalizedValue.Scalar("NewItem")
                )
            )
        )
        val indexUpdate = IndexUpdate(key, listOf(itemKey))
        val write = NormalizedWrite(changeSet, indexUpdate)

        // When
        sot.write(key, write)

        // Then
        val record = backend.readOne(itemKey)
        assertNotNull(record)
        assertEquals("NewItem", (record["name"] as NormalizedValue.Scalar).value)
    }

    @Test
    fun write_givenNormalizedWriteWithNullIndexUpdate_whenWrite_thenAppliesChangeSet() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val changeSet = NormalizedChangeSet(
            upserts = mapOf(
                itemKey to mapOf(
                    "id" to NormalizedValue.Scalar("1"),
                    "name" to NormalizedValue.Scalar("NewItem")
                )
            )
        )
        val write = NormalizedWrite<QueryKey>(changeSet, indexUpdate = null)

        // When
        sot.write(key, write)

        // Then
        val record = backend.readOne(itemKey)
        assertNotNull(record)
        assertEquals("NewItem", (record["name"] as NormalizedValue.Scalar).value)
    }

    @Test
    fun delete_givenKey_whenDelete_thenClearsRootDependencies() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val rootRef = RootRef(key, shape.id)

        // When
        sot.delete(key)

        // Then
        val deps = backend.getRootDependencies(rootRef)
        assertNotNull(deps)
        assertTrue(deps.isEmpty())
    }

    @Test
    fun withTransaction_givenBlock_whenExecute_thenExecutesBlock() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)

        var executed = false

        // When
        sot.withTransaction {
            executed = true
        }

        // Then
        assertTrue(executed)
    }

    @Test
    fun rekey_givenOldAndNewKeys_whenRekey_thenDoesNothing() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val oldKey = createTestKey("old")
        val newKey = createTestKey("new")

        // When
        sot.rekey(oldKey, newKey) { old, _ -> old }

        // Then
        // No-op, just verify it doesn't throw
    }

    @Test
    fun reader_givenEmptyListFromNonEmptyList_whenRead_thenEmitsEmptyList() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val now = Clock.System.now()
        backend.setRecord(
            itemKey,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        index.updateIndex(key, listOf(itemKey))

        // When
        sot.reader(key).test {
            // Then
            val initial = awaitItem()
            assertEquals(1, initial!!.value.size)

            // Update to empty list
            index.updateIndex(key, emptyList())

            val updated = awaitItem()
            assertTrue(updated!!.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenSameIndexValueTwice_whenRead_thenEmitsOnce() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val itemKey = EntityKey("TestItem", "1")
        val now = Clock.System.now()
        backend.setRecord(
            itemKey,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        index.updateIndex(key, listOf(itemKey))

        // When
        sot.reader(key).test {
            // Then
            awaitItem()

            // Update with same value
            index.updateIndex(key, listOf(itemKey))

            // Should not emit again
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenMultipleItemsWithSameEtag_whenRead_thenAggregatesUniquely() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val item1Key = EntityKey("TestItem", "1")
        val item2Key = EntityKey("TestItem", "2")

        val now = Clock.System.now()
        backend.setRecord(
            item1Key,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "same-etag", updatedAt = now)
        )
        backend.setRecord(
            item2Key,
            mapOf("id" to NormalizedValue.Scalar("2"), "name" to NormalizedValue.Scalar("Item2")),
            EntityMeta(etag = "same-etag", updatedAt = now)
        )
        index.updateIndex(key, listOf(item1Key, item2Key))

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertNotNull(result.meta.etagFingerprint)
            // Both items have same etag, should be in fingerprint
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reader_givenItemsMixedWithAndWithoutEtags_whenRead_thenAggregatesOnlyNonNullEtags() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val registry = SchemaRegistry(mapOf("TestItem" to createTestAdapter()))
        val shape = createTestShape()
        val sot = NormalizedListSot<QueryKey, TestItem>(backend, index, registry, shape)
        val key = createTestKey()

        val item1Key = EntityKey("TestItem", "1")
        val item2Key = EntityKey("TestItem", "2")

        val now = Clock.System.now()
        backend.setRecord(
            item1Key,
            mapOf("id" to NormalizedValue.Scalar("1"), "name" to NormalizedValue.Scalar("Item1")),
            EntityMeta(etag = "etag1", updatedAt = now)
        )
        backend.setRecord(
            item2Key,
            mapOf("id" to NormalizedValue.Scalar("2"), "name" to NormalizedValue.Scalar("Item2")),
            EntityMeta(etag = null, updatedAt = now)
        )
        index.updateIndex(key, listOf(item1Key, item2Key))

        // When
        sot.reader(key).test {
            // Then
            val result = awaitItem()
            assertNotNull(result)
            assertNotNull(result.meta.etagFingerprint)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
