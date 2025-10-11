package dev.mattramotar.storex.normalization

import dev.mattramotar.storex.core.QueryKey
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreNamespace
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NormalizerEngineTest {

    data class TestUser(
        val id: String,
        val name: String,
        val email: String,
        val profile: TestProfile? = null
    )

    data class TestProfile(
        val id: String,
        val bio: String
    )

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

    class FakeSchemaRegistry(private val adapters: Map<String, EntityAdapter<*>>) {
        fun toSchemaRegistry(): SchemaRegistry = SchemaRegistry(adapters)
    }

    class FakeNormalizationBackend : NormalizationBackend {
        val appliedChangeSets = mutableListOf<NormalizedChangeSet>()
        val updatedRootDependencies = mutableListOf<Pair<RootRef, Set<EntityKey>>>()

        private val _entityInvalidations = MutableSharedFlow<Set<EntityKey>>(replay = 1)
        private val _rootInvalidations = MutableSharedFlow<Set<RootRef>>(replay = 1)

        override val entityInvalidations: Flow<Set<EntityKey>> = _entityInvalidations
        override val rootInvalidations: Flow<Set<RootRef>> = _rootInvalidations

        override suspend fun read(keys: Set<EntityKey>): Map<EntityKey, NormalizedRecord?> {
            return keys.associateWith { null }
        }

        override suspend fun readMeta(keys: Set<EntityKey>): Map<EntityKey, EntityMeta?> {
            return keys.associateWith { null }
        }

        override suspend fun apply(changeSet: NormalizedChangeSet) {
            appliedChangeSets.add(changeSet)
        }

        override suspend fun updateRootDependencies(root: RootRef, dependsOn: Set<EntityKey>) {
            updatedRootDependencies.add(root to dependsOn)
        }

        override suspend fun clear() {
            appliedChangeSets.clear()
            updatedRootDependencies.clear()
        }
    }

    class FakeIndexManager : IndexManager {
        val updates = mutableListOf<Pair<StoreKey, List<EntityKey>>>()

        override suspend fun updateIndex(requestKey: StoreKey, roots: List<EntityKey>) {
            updates.add(requestKey to roots)
        }

        override fun streamIndex(requestKey: StoreKey): Flow<List<EntityKey>?> {
            return MutableSharedFlow()
        }
    }

    private fun createTestUserAdapter(): FakeEntityAdapter<TestUser> {
        return FakeEntityAdapter(
            typeName = "TestUser",
            idExtractor = { it.id },
            normalizer = { user, ctx ->
                val record = mutableMapOf<String, NormalizedValue>()
                record["id"] = NormalizedValue.Scalar(user.id)
                record["name"] = NormalizedValue.Scalar(user.name)
                record["email"] = NormalizedValue.Scalar(user.email)

                if (user.profile != null) {
                    val profileKey = ctx.registerNested(user.profile)
                    record["profile"] = NormalizedValue.Ref(profileKey)
                }

                record to setOf("id", "name", "email", "profile")
            },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String,
                    email = (record["email"] as NormalizedValue.Scalar).value as String
                )
            }
        )
    }

    private fun createTestProfileAdapter(): FakeEntityAdapter<TestProfile> {
        return FakeEntityAdapter(
            typeName = "TestProfile",
            idExtractor = { it.id },
            normalizer = { profile, _ ->
                val record = mapOf<String, NormalizedValue>(
                    "id" to NormalizedValue.Scalar(profile.id),
                    "bio" to NormalizedValue.Scalar(profile.bio)
                )
                record to setOf("id", "bio")
            },
            denormalizer = { record, _ ->
                TestProfile(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    bio = (record["bio"] as NormalizedValue.Scalar).value as String
                )
            }
        )
    }

    private fun createTestStoreKey(query: String = "test"): StoreKey {
        return QueryKey(
            namespace = StoreNamespace("test"),
            query = mapOf("q" to query)
        )
    }

    @Test
    fun normalizeAndWrite_givenSingleEntity_thenNormalizesAndAppliesChangeSet() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val testUser = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, testUser)

        // Then
        assertEquals(1, changeSet.upserts.size)
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))

        val userRecord = changeSet.upserts[EntityKey("TestUser", "user-1")]
        assertNotNull(userRecord)
        assertEquals(NormalizedValue.Scalar("user-1"), userRecord["id"])
        assertEquals(NormalizedValue.Scalar("Alice"), userRecord["name"])
        assertEquals(NormalizedValue.Scalar("alice@example.com"), userRecord["email"])
    }

    @Test
    fun normalizeAndWrite_givenSingleEntity_thenAppliesChangeSetToBackend() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val testUser = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val requestKey = createTestStoreKey()

        // When
        engine.normalizeAndWrite(requestKey, testUser)

        // Then
        assertEquals(1, backend.appliedChangeSets.size)
        val appliedChangeSet = backend.appliedChangeSets.first()
        assertEquals(1, appliedChangeSet.upserts.size)
        assertTrue(appliedChangeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))
    }

    @Test
    fun normalizeAndWrite_givenSingleEntity_thenUpdatesIndex() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val testUser = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val requestKey = createTestStoreKey()

        // When
        engine.normalizeAndWrite(requestKey, testUser)

        // Then
        assertEquals(1, index.updates.size)
        val (key, roots) = index.updates.first()
        assertEquals(requestKey, key)
        assertEquals(listOf(EntityKey("TestUser", "user-1")), roots)
    }

    @Test
    fun normalizeAndWrite_givenSingleEntity_thenCreatesMetadataWithTimestamp() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val testUser = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val requestKey = createTestStoreKey()
        val beforeNormalize = Clock.System.now()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, testUser)

        // Then
        val afterNormalize = Clock.System.now()
        assertEquals(1, changeSet.meta.size)

        val metadata = changeSet.meta[EntityKey("TestUser", "user-1")]
        assertNotNull(metadata)
        assertTrue(metadata.updatedAt >= beforeNormalize)
        assertTrue(metadata.updatedAt <= afterNormalize)
        assertEquals(false, metadata.tombstone)
    }

    @Test
    fun normalizeAndWrite_givenSingleEntity_thenCreatesFieldMasks() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val testUser = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, testUser)

        // Then
        assertEquals(1, changeSet.fieldMasks.size)
        val mask = changeSet.fieldMasks[EntityKey("TestUser", "user-1")]
        assertNotNull(mask)
        assertTrue(mask.containsAll(setOf("id", "name", "email", "profile")))
    }

    @Test
    fun normalizeAndWrite_givenListOfEntities_thenNormalizesAll() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val users = listOf(
            TestUser(id = "user-1", name = "Alice", email = "alice@example.com"),
            TestUser(id = "user-2", name = "Bob", email = "bob@example.com"),
            TestUser(id = "user-3", name = "Charlie", email = "charlie@example.com")
        )
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, users)

        // Then
        assertEquals(3, changeSet.upserts.size)
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-2")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-3")))
    }

    @Test
    fun normalizeAndWrite_givenListOfEntities_thenUpdatesIndexWithAllRoots() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val users = listOf(
            TestUser(id = "user-1", name = "Alice", email = "alice@example.com"),
            TestUser(id = "user-2", name = "Bob", email = "bob@example.com")
        )
        val requestKey = createTestStoreKey()

        // When
        engine.normalizeAndWrite(requestKey, users)

        // Then
        assertEquals(1, index.updates.size)
        val (key, roots) = index.updates.first()
        assertEquals(requestKey, key)
        assertEquals(2, roots.size)
        assertTrue(roots.contains(EntityKey("TestUser", "user-1")))
        assertTrue(roots.contains(EntityKey("TestUser", "user-2")))
    }

    @Test
    fun normalizeAndWrite_givenEmptyList_thenCreatesEmptyChangeSet() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, emptyList<TestUser>())

        // Then
        assertEquals(0, changeSet.upserts.size)
        assertEquals(0, changeSet.meta.size)
        assertEquals(0, changeSet.fieldMasks.size)
    }

    @Test
    fun normalizeAndWrite_givenEmptyList_thenUpdatesIndexWithEmptyRoots() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val requestKey = createTestStoreKey()

        // When
        engine.normalizeAndWrite(requestKey, emptyList<TestUser>())

        // Then
        assertEquals(1, index.updates.size)
        val (key, roots) = index.updates.first()
        assertEquals(requestKey, key)
        assertEquals(0, roots.size)
    }

    @Test
    fun normalizeAndWrite_givenListWithNulls_thenFiltersNulls() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val users = listOf(
            TestUser(id = "user-1", name = "Alice", email = "alice@example.com"),
            null,
            TestUser(id = "user-2", name = "Bob", email = "bob@example.com"),
            null
        )
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, users)

        // Then
        assertEquals(2, changeSet.upserts.size)
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-2")))
    }

    @Test
    fun normalizeAndWrite_givenEntityWithNestedEntity_thenNormalizesBoth() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = FakeSchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        ).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val profile = TestProfile(id = "profile-1", bio = "Software Engineer")
        val user = TestUser(id = "user-1", name = "Alice", email = "alice@example.com", profile = profile)
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, user)

        // Then
        assertEquals(2, changeSet.upserts.size)
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestProfile", "profile-1")))
    }

    @Test
    fun normalizeAndWrite_givenEntityWithNestedEntity_thenCreatesReference() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = FakeSchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        ).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val profile = TestProfile(id = "profile-1", bio = "Software Engineer")
        val user = TestUser(id = "user-1", name = "Alice", email = "alice@example.com", profile = profile)
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, user)

        // Then
        val userRecord = changeSet.upserts[EntityKey("TestUser", "user-1")]
        assertNotNull(userRecord)

        val profileRef = userRecord["profile"] as? NormalizedValue.Ref
        assertNotNull(profileRef)
        assertEquals(EntityKey("TestProfile", "profile-1"), profileRef.key)
    }

    @Test
    fun normalizeAndWrite_givenEntityWithNestedEntity_thenOnlyRootInIndex() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = FakeSchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        ).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val profile = TestProfile(id = "profile-1", bio = "Software Engineer")
        val user = TestUser(id = "user-1", name = "Alice", email = "alice@example.com", profile = profile)
        val requestKey = createTestStoreKey()

        // When
        engine.normalizeAndWrite(requestKey, user)

        // Then
        assertEquals(1, index.updates.size)
        val (_, roots) = index.updates.first()
        assertEquals(1, roots.size)
        assertEquals(EntityKey("TestUser", "user-1"), roots.first())
    }

    @Test
    fun normalizeAndWrite_givenDuplicateEntitiesInList_thenDeduplicates() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val user1 = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val user1Duplicate = TestUser(id = "user-1", name = "Alice Updated", email = "alice@example.com")
        val users = listOf(user1, user1Duplicate)
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, users)

        // Then
        assertEquals(1, changeSet.upserts.size)
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))

        // First normalized version should be kept (deduplication happens in registerNested)
        val userRecord = changeSet.upserts[EntityKey("TestUser", "user-1")]
        assertNotNull(userRecord)
        assertEquals(NormalizedValue.Scalar("Alice"), userRecord["name"])
    }

    @Test
    fun normalizeAndWrite_givenDuplicateEntitiesInList_thenBothInRootsList() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val user1 = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val user1Duplicate = TestUser(id = "user-1", name = "Alice Updated", email = "alice@example.com")
        val users = listOf(user1, user1Duplicate)
        val requestKey = createTestStoreKey()

        // When
        engine.normalizeAndWrite(requestKey, users)

        // Then
        val (_, roots) = index.updates.first()
        assertEquals(2, roots.size)
        assertEquals(EntityKey("TestUser", "user-1"), roots[0])
        assertEquals(EntityKey("TestUser", "user-1"), roots[1])
    }

    @Test
    fun normalizeAndWrite_givenMultipleEntitiesWithSharedNestedEntity_thenNormalizesNestedOnce() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = FakeSchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        ).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val sharedProfile = TestProfile(id = "profile-1", bio = "Shared Bio")
        val user1 = TestUser(id = "user-1", name = "Alice", email = "alice@example.com", profile = sharedProfile)
        val user2 = TestUser(id = "user-2", name = "Bob", email = "bob@example.com", profile = sharedProfile)
        val users = listOf(user1, user2)
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, users)

        // Then
        assertEquals(3, changeSet.upserts.size)
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-2")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestProfile", "profile-1")))

        // Verify both users reference the same profile
        val user1Record = changeSet.upserts[EntityKey("TestUser", "user-1")]
        val user2Record = changeSet.upserts[EntityKey("TestUser", "user-2")]
        assertNotNull(user1Record)
        assertNotNull(user2Record)

        val user1ProfileRef = user1Record["profile"] as? NormalizedValue.Ref
        val user2ProfileRef = user2Record["profile"] as? NormalizedValue.Ref
        assertNotNull(user1ProfileRef)
        assertNotNull(user2ProfileRef)
        assertEquals(user1ProfileRef.key, user2ProfileRef.key)
    }

    @Test
    fun normalizeAndWrite_givenListOfEntities_thenCreatesMetadataForAll() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val users = listOf(
            TestUser(id = "user-1", name = "Alice", email = "alice@example.com"),
            TestUser(id = "user-2", name = "Bob", email = "bob@example.com")
        )
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, users)

        // Then
        assertEquals(2, changeSet.meta.size)
        assertNotNull(changeSet.meta[EntityKey("TestUser", "user-1")])
        assertNotNull(changeSet.meta[EntityKey("TestUser", "user-2")])
    }

    @Test
    fun normalizeAndWrite_givenEntityWithNestedEntity_thenCreatesMetadataForBoth() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = FakeSchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        ).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val profile = TestProfile(id = "profile-1", bio = "Software Engineer")
        val user = TestUser(id = "user-1", name = "Alice", email = "alice@example.com", profile = profile)
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, user)

        // Then
        assertEquals(2, changeSet.meta.size)
        assertNotNull(changeSet.meta[EntityKey("TestUser", "user-1")])
        assertNotNull(changeSet.meta[EntityKey("TestProfile", "profile-1")])
    }

    @Test
    fun normalizeAndWrite_givenDifferentRequestKeys_thenUpdatesCorrectIndex() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val user1 = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val user2 = TestUser(id = "user-2", name = "Bob", email = "bob@example.com")
        val requestKey1 = createTestStoreKey("query1")
        val requestKey2 = createTestStoreKey("query2")

        // When
        engine.normalizeAndWrite(requestKey1, user1)
        engine.normalizeAndWrite(requestKey2, user2)

        // Then
        assertEquals(2, index.updates.size)

        val (key1, roots1) = index.updates[0]
        assertEquals(requestKey1, key1)
        assertEquals(listOf(EntityKey("TestUser", "user-1")), roots1)

        val (key2, roots2) = index.updates[1]
        assertEquals(requestKey2, key2)
        assertEquals(listOf(EntityKey("TestUser", "user-2")), roots2)
    }

    @Test
    fun normalizeAndWrite_givenSameRequestKeyMultipleTimes_thenUpdatesIndexEachTime() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = FakeSchemaRegistry(mapOf("TestUser" to userAdapter)).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val user1 = TestUser(id = "user-1", name = "Alice", email = "alice@example.com")
        val user2 = TestUser(id = "user-2", name = "Bob", email = "bob@example.com")
        val requestKey = createTestStoreKey()

        // When
        engine.normalizeAndWrite(requestKey, user1)
        engine.normalizeAndWrite(requestKey, user2)

        // Then
        assertEquals(2, index.updates.size)
        assertEquals(requestKey, index.updates[0].first)
        assertEquals(requestKey, index.updates[1].first)
    }

    @Test
    fun normalizeAndWrite_givenEntityWithoutNestedEntities_thenOnlyNormalizesRoot() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = FakeSchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        ).toSchemaRegistry()
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val user = TestUser(id = "user-1", name = "Alice", email = "alice@example.com", profile = null)
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, user)

        // Then
        assertEquals(1, changeSet.upserts.size)
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))
    }

    @Test
    fun normalizeAndWrite_givenComplexNestedStructure_thenNormalizesAllLevels() = runTest {
        // Given
        data class TestComment(val id: String, val text: String)
        data class TestPost(val id: String, val title: String, val author: TestUser?, val comments: List<TestComment>)

        val commentAdapter = FakeEntityAdapter<TestComment>(
            typeName = "TestComment",
            idExtractor = { it.id },
            normalizer = { comment, _ ->
                mapOf(
                    "id" to NormalizedValue.Scalar(comment.id),
                    "text" to NormalizedValue.Scalar(comment.text)
                ) to setOf("id", "text")
            },
            denormalizer = { record, _ ->
                TestComment(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    text = (record["text"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val postAdapter = FakeEntityAdapter<TestPost>(
            typeName = "TestPost",
            idExtractor = { it.id },
            normalizer = { post, ctx ->
                val record = mutableMapOf<String, NormalizedValue>()
                record["id"] = NormalizedValue.Scalar(post.id)
                record["title"] = NormalizedValue.Scalar(post.title)

                if (post.author != null) {
                    val authorKey = ctx.registerNested(post.author)
                    record["author"] = NormalizedValue.Ref(authorKey)
                }

                val commentKeys = post.comments.map { ctx.registerNested(it) }
                record["comments"] = NormalizedValue.RefList(commentKeys)

                record to setOf("id", "title", "author", "comments")
            },
            denormalizer = { record, _ ->
                TestPost(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    title = (record["title"] as NormalizedValue.Scalar).value as String,
                    author = null,
                    comments = emptyList()
                )
            }
        )

        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()

        val registry = FakeSchemaRegistry(
            mapOf(
                "TestPost" to postAdapter,
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter,
                "TestComment" to commentAdapter
            )
        ).toSchemaRegistry()

        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val engine = NormalizerEngine(registry, backend, index)

        val profile = TestProfile(id = "profile-1", bio = "Author Bio")
        val author = TestUser(id = "user-1", name = "Alice", email = "alice@example.com", profile = profile)
        val comments = listOf(
            TestComment(id = "comment-1", text = "Great post!"),
            TestComment(id = "comment-2", text = "Thanks for sharing")
        )
        val post = TestPost(id = "post-1", title = "Test Post", author = author, comments = comments)
        val requestKey = createTestStoreKey()

        // When
        val changeSet = engine.normalizeAndWrite(requestKey, post)

        // Then
        assertEquals(5, changeSet.upserts.size)
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestPost", "post-1")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestUser", "user-1")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestProfile", "profile-1")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestComment", "comment-1")))
        assertTrue(changeSet.upserts.containsKey(EntityKey("TestComment", "comment-2")))
    }
}
