package dev.mattramotar.storex.normalization.internal

import app.cash.turbine.test
import dev.mattramotar.storex.core.ByIdKey
import dev.mattramotar.storex.core.EntityId
import dev.mattramotar.storex.core.StoreNamespace
import dev.mattramotar.storex.normalization.ChangeSetBuilder
import dev.mattramotar.storex.normalization.EntityMeta
import dev.mattramotar.storex.normalization.GraphProjection
import dev.mattramotar.storex.normalization.IndexUpdate
import dev.mattramotar.storex.normalization.NormalizedChangeSet
import dev.mattramotar.storex.normalization.NormalizedWrite
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NormalizedEntitySotTest {

    // Test domain model
    data class TestUser(val id: String, val name: String)

    // Fake Shape (DoNotMock - conceptually non-mockable)
    class FakeShape<V : Any>(
        override val id: ShapeId,
        override val outputType: KClass<V>,
        override val edgeFields: Set<String> = emptySet(),
        private val refExtractor: (NormalizedRecord) -> Set<EntityKey> = { emptySet() }
    ) : Shape<V> {
        override fun outboundRefs(record: NormalizedRecord): Set<EntityKey> = refExtractor(record)
    }

    // Fake EntityAdapter (DoNotMock - builder-backed)
    class FakeEntityAdapter<T : Any>(
        override val typeName: String,
        private val idExtractor: (T) -> String,
        private val normalizer: (T, NormalizationContext) -> Pair<NormalizedRecord, Set<String>>,
        private val denormalizer: suspend (NormalizedRecord, DenormalizationContext) -> T
    ) : EntityAdapter<T> {
        override fun extractId(entity: T): String = idExtractor(entity)

        override fun normalize(
            entity: T,
            ctx: NormalizationContext
        ): Pair<NormalizedRecord, Set<String>> = normalizer(entity, ctx)

        override suspend fun denormalize(
            record: NormalizedRecord,
            ctx: DenormalizationContext
        ): T = denormalizer(record, ctx)
    }

    // Fake NormalizationBackend (DoNotMock)
    class FakeNormalizationBackend : NormalizationBackend {
        private val storage = mutableMapOf<EntityKey, NormalizedRecord>()
        private val metadata = mutableMapOf<EntityKey, EntityMeta>()
        private val _entityInvalidations = MutableSharedFlow<Set<EntityKey>>(replay = 0)
        private val _rootInvalidations = MutableSharedFlow<Set<RootRef>>(replay = 0)
        val appliedChangeSets = mutableListOf<NormalizedChangeSet>()
        val updatedDependencies = mutableListOf<Pair<RootRef, Set<EntityKey>>>()

        override val entityInvalidations: Flow<Set<EntityKey>> = _entityInvalidations
        override val rootInvalidations: Flow<Set<RootRef>> = _rootInvalidations

        fun setRecord(key: EntityKey, record: NormalizedRecord) {
            storage[key] = record
        }

        fun setMeta(key: EntityKey, meta: EntityMeta) {
            metadata[key] = meta
        }

        suspend fun emitRootInvalidation(roots: Set<RootRef>) {
            _rootInvalidations.emit(roots)
        }

        override suspend fun read(keys: Set<EntityKey>): Map<EntityKey, NormalizedRecord?> {
            return keys.associateWith { storage[it] }
        }

        override suspend fun readMeta(keys: Set<EntityKey>): Map<EntityKey, EntityMeta?> {
            return keys.associateWith { metadata[it] }
        }

        override suspend fun apply(changeSet: NormalizedChangeSet) {
            appliedChangeSets.add(changeSet)
        }

        override suspend fun updateRootDependencies(root: RootRef, dependsOn: Set<EntityKey>) {
            updatedDependencies.add(root to dependsOn)
        }

        override suspend fun clear() {
            storage.clear()
            metadata.clear()
        }
    }

    @Test
    fun reader_givenInitialEmission_whenCollect_thenTriggersComposition() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val userKey = EntityKey("User", "1")
        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice")
        )
        val now = Clock.System.now()

        backend.setRecord(userKey, userRecord)
        backend.setMeta(userKey, EntityMeta(updatedAt = now))

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = emptySet()
        )

        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }
        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "1"))

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        // When
        sot.reader(key).test {
            // Then
            val emission = awaitItem()!!
            assertEquals("Alice", emission.value.name)
            assertEquals(now, emission.meta.updatedAt)

            // Verify dependencies were updated
            assertEquals(1, backend.updatedDependencies.size)
            assertEquals(RootRef(key, shape.id), backend.updatedDependencies[0].first)
            assertEquals(setOf(userKey), backend.updatedDependencies[0].second)

            cancel()
        }
    }

    @Test
    fun reader_givenEmptyRootInvalidations_whenEmit_thenTriggersComposition() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val userKey = EntityKey("User", "1")
        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Bob")
        )
        val now = Clock.System.now()

        backend.setRecord(userKey, userRecord)
        backend.setMeta(userKey, EntityMeta(updatedAt = now))

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }
        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "1"))

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        // When
        sot.reader(key).test {
            // Then
            awaitItem() // Initial emission

            // Emit empty set invalidation
            backend.emitRootInvalidation(emptySet())

            val secondEmission = awaitItem()!!
            assertEquals("Bob", secondEmission.value.name)

            cancel()
        }
    }

    @Test
    fun reader_givenMatchingRootRef_whenEmit_thenTriggersComposition() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val userKey = EntityKey("User", "2")
        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("2"),
            "name" to NormalizedValue.Scalar("Charlie")
        )
        val now = Clock.System.now()

        backend.setRecord(userKey, userRecord)
        backend.setMeta(userKey, EntityMeta(updatedAt = now))

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }
        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "2"))

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        // When
        sot.reader(key).test {
            // Then
            awaitItem() // Initial emission

            // Emit matching rootRef
            val matchingRef = RootRef(key, shape.id)
            backend.emitRootInvalidation(setOf(matchingRef))

            val secondEmission = awaitItem()!!
            assertEquals("Charlie", secondEmission.value.name)

            cancel()
        }
    }

    @Test
    fun reader_givenNonMatchingRootRef_whenEmit_thenDoesNotTriggerComposition() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val userKey = EntityKey("User", "3")
        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("3"),
            "name" to NormalizedValue.Scalar("Diana")
        )
        val now = Clock.System.now()

        backend.setRecord(userKey, userRecord)
        backend.setMeta(userKey, EntityMeta(updatedAt = now))

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }
        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "3"))

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        // When
        sot.reader(key).test {
            // Then
            awaitItem() // Initial emission

            // Emit non-matching rootRef (different key)
            val differentKey = ByIdKey(StoreNamespace("test"), EntityId("User", "999"))
            val nonMatchingRef = RootRef(differentKey, shape.id)
            backend.emitRootInvalidation(setOf(nonMatchingRef))

            // Should not get another emission (filtered out)
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun reader_givenValidData_whenCompose_thenEmitsGraphProjectionWithValueAndMeta() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val userKey = EntityKey("User", "5")
        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("5"),
            "name" to NormalizedValue.Scalar("Eve")
        )
        val now = Clock.System.now()

        backend.setRecord(userKey, userRecord)
        backend.setMeta(userKey, EntityMeta(etag = "etag-123", updatedAt = now))

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }
        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "5"))

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        // When
        sot.reader(key).test {
            // Then
            val emission = awaitItem()!!

            // Verify GraphProjection structure
            assertEquals("Eve", emission.value.name)
            assertEquals("5", emission.value.id)
            assertEquals(now, emission.meta.updatedAt)
            assertNotNull(emission.meta.etagFingerprint)

            cancel()
        }
    }

    @Test
    fun reader_givenComposition_whenComplete_thenUpdatesDependencies() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val userKey = EntityKey("User", "6")
        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("6"),
            "name" to NormalizedValue.Scalar("Frank")
        )
        val now = Clock.System.now()

        backend.setRecord(userKey, userRecord)
        backend.setMeta(userKey, EntityMeta(updatedAt = now))

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }
        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "6"))

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        // When
        sot.reader(key).test {
            // Then
            awaitItem()

            // Verify dependencies were updated
            assertEquals(1, backend.updatedDependencies.size)
            val (root, deps) = backend.updatedDependencies[0]
            assertEquals(RootRef(key, shape.id), root)
            assertEquals(setOf(userKey), deps)

            cancel()
        }
    }

    @Test
    fun write_givenNormalizedWriteWithoutIndexUpdate_whenWrite_thenAppliesChangeSet() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val registry = SchemaRegistry(emptyMap())
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "7"))
        val changeSet = ChangeSetBuilder()
            .upsert(
                EntityKey("User", "7"),
                mapOf("id" to NormalizedValue.Scalar("7"), "name" to NormalizedValue.Scalar("Grace"))
            )
            .build()
        val write = NormalizedWrite<ByIdKey>(changeSet = changeSet, indexUpdate = null)

        // When
        sot.write(key, write)

        // Then
        assertEquals(1, backend.appliedChangeSets.size)
        assertEquals(changeSet, backend.appliedChangeSets[0])
        assertEquals(0, backend.updatedDependencies.size)
    }

    @Test
    fun write_givenNormalizedWriteWithIndexUpdate_whenWrite_thenAppliesChangeSetAndUpdatesIndex() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val registry = SchemaRegistry(emptyMap())
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "8"))
        val changeSet = ChangeSetBuilder()
            .upsert(
                EntityKey("User", "8"),
                mapOf("id" to NormalizedValue.Scalar("8"), "name" to NormalizedValue.Scalar("Henry"))
            )
            .build()
        val indexUpdateKey = ByIdKey(StoreNamespace("test"), EntityId("Index", "index-key"))
        val indexUpdate = IndexUpdate(
            requestKey = indexUpdateKey,
            roots = listOf(EntityKey("User", "8"))
        )
        val write = NormalizedWrite(changeSet = changeSet, indexUpdate = indexUpdate)

        // When
        sot.write(key, write)

        // Then
        assertEquals(1, backend.appliedChangeSets.size)
        assertEquals(changeSet, backend.appliedChangeSets[0])

        // Verify index update triggered dependency update
        assertEquals(1, backend.updatedDependencies.size)
        assertEquals(RootRef(indexUpdateKey, shape.id), backend.updatedDependencies[0].first)
        assertEquals(emptySet(), backend.updatedDependencies[0].second)
    }

    @Test
    fun delete_givenKey_whenDelete_thenCleansUpRootDependencies() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val registry = SchemaRegistry(emptyMap())
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        val key = ByIdKey(StoreNamespace("test"), EntityId("User", "9"))

        // When
        sot.delete(key)

        // Then
        assertEquals(1, backend.updatedDependencies.size)
        assertEquals(RootRef(key, shape.id), backend.updatedDependencies[0].first)
        assertEquals(emptySet(), backend.updatedDependencies[0].second)
    }

    @Test
    fun withTransaction_givenBlock_whenExecute_thenExecutesBlock() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val registry = SchemaRegistry(emptyMap())
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        var executed = false
        val block: suspend () -> Unit = { executed = true }

        // When
        sot.withTransaction(block)

        // Then
        assertTrue(executed)
    }

    @Test
    fun rekey_givenOldAndNewKeys_whenRekey_thenDoesNothing() = runTest {
        // Given
        val backend = FakeNormalizationBackend()

        val registry = SchemaRegistry(emptyMap())
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class
        )
        val resolver = RootResolver<ByIdKey> { EntityKey(it.entity.type, it.entity.id) }

        val sot = NormalizedEntitySot(backend, registry, shape, resolver)

        val oldKey = ByIdKey(StoreNamespace("test"), EntityId("User", "old"))
        val newKey = ByIdKey(StoreNamespace("test"), EntityId("User", "new"))
        val now = Clock.System.now()
        val reconcile: suspend (GraphProjection<TestUser>, GraphProjection<TestUser>?) -> GraphProjection<TestUser> =
            { old, _ -> old }

        // When
        sot.rekey(oldKey, newKey, reconcile)

        // Then
        // Verify it's a no-op - no backend interactions should occur
        assertEquals(0, backend.appliedChangeSets.size)
        assertEquals(0, backend.updatedDependencies.size)
    }
}
