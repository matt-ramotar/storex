package dev.mattramotar.storex.normalization.internal

import dev.mattramotar.storex.normalization.EntityMeta
import dev.mattramotar.storex.normalization.GraphCompositionException
import dev.mattramotar.storex.normalization.GraphMeta
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
import kotlinx.datetime.Instant
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeResultTest {

    data class TestUser(
        val id: String,
        val name: String,
        val profile: TestProfile? = null
    )

    data class TestProfile(
        val id: String,
        val bio: String
    )

    data class TestPost(
        val id: String,
        val title: String,
        val author: TestUser? = null,
        val comments: List<TestComment> = emptyList()
    )

    data class TestComment(
        val id: String,
        val text: String
    )

    // Fake EntityAdapter for testing (DoNotMock)
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

    // Fake Shape implementation for testing (DoNotMock)
    class FakeShape<V : Any>(
        override val id: ShapeId,
        override val outputType: KClass<V>,
        override val edgeFields: Set<String>,
        override val maxDepth: Int = 10,
        private val refExtractor: (NormalizedRecord) -> Set<EntityKey>
    ) : Shape<V> {
        override fun outboundRefs(record: NormalizedRecord): Set<EntityKey> = refExtractor(record)
    }

    // Fake NormalizationBackend for testing
    class FakeNormalizationBackend : NormalizationBackend {
        private val storage = mutableMapOf<EntityKey, NormalizedRecord>()
        private val metadata = mutableMapOf<EntityKey, EntityMeta>()
        private val _entityInvalidations = MutableSharedFlow<Set<EntityKey>>(replay = 0)
        private val _rootInvalidations = MutableSharedFlow<Set<RootRef>>(replay = 0)
        var shouldThrowOnRead = false
        var readCallCount = 0

        override val entityInvalidations: Flow<Set<EntityKey>> = _entityInvalidations
        override val rootInvalidations: Flow<Set<RootRef>> = _rootInvalidations

        fun setRecord(key: EntityKey, record: NormalizedRecord) {
            storage[key] = record
        }

        fun setMeta(key: EntityKey, meta: EntityMeta) {
            metadata[key] = meta
        }

        override suspend fun read(keys: Set<EntityKey>): Map<EntityKey, NormalizedRecord?> {
            readCallCount++
            if (shouldThrowOnRead) {
                throw RuntimeException("Backend read failure")
            }
            return keys.associateWith { storage[it] }
        }

        override suspend fun readMeta(keys: Set<EntityKey>): Map<EntityKey, EntityMeta?> {
            return keys.associateWith { metadata[it] }
        }

        override suspend fun apply(changeSet: dev.mattramotar.storex.normalization.NormalizedChangeSet) {}

        override suspend fun updateRootDependencies(root: RootRef, dependsOn: Set<EntityKey>) {}

        override suspend fun clear() {
            storage.clear()
            metadata.clear()
        }
    }

    @Test
    fun composeFromRoot_givenSingleEntityWithNoReferences_whenCompose_thenReturnsValueWithSingleDependency() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")
        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice")
        )
        backend.setRecord(userKey, userRecord)

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
            edgeFields = emptySet(),
            refExtractor = { emptySet() }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertEquals("Alice", result.value.name)
        assertEquals(setOf(userKey), result.dependencies)
        assertNotNull(result.meta)
    }

    @Test
    fun composeFromRoot_givenEntityWithSingleReference_whenCompose_thenReturnsValueWithMultipleDependencies() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")
        val profileKey = EntityKey("Profile", "100")

        val profileRecord = mapOf(
            "id" to NormalizedValue.Scalar("100"),
            "bio" to NormalizedValue.Scalar("Software Engineer")
        )
        backend.setRecord(profileKey, profileRecord)

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice"),
            "profile" to NormalizedValue.Ref(profileKey)
        )
        backend.setRecord(userKey, userRecord)

        val profileAdapter = FakeEntityAdapter<TestProfile>(
            typeName = "Profile",
            idExtractor = { it.id },
            normalizer = { _, _ -> profileRecord to emptySet() },
            denormalizer = { record, _ ->
                TestProfile(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    bio = (record["bio"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, ctx ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String,
                    profile = profileRef?.let { ctx.resolveReference(it.key) as? TestProfile }
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter, "Profile" to profileAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile"),
            refExtractor = { record ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                setOfNotNull(profileRef?.key)
            }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertEquals("Alice", result.value.name)
        assertEquals("Software Engineer", result.value.profile?.bio)
        assertEquals(setOf(userKey, profileKey), result.dependencies)
    }

    @Test
    fun composeFromRoot_givenCyclicReference_whenCompose_thenDetectsCycleAndStopsTraversal() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice"),
            "profile" to NormalizedValue.Ref(userKey) // Self reference
        )
        backend.setRecord(userKey, userRecord)

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
            edgeFields = setOf("profile"),
            refExtractor = { record ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                setOfNotNull(profileRef?.key)
            }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertEquals("Alice", result.value.name)
        assertEquals(setOf(userKey), result.dependencies) // Only userKey, cycle prevented
    }

    @Test
    fun composeFromRoot_givenMaxDepthReached_whenCompose_thenStopsTraversalAtMaxDepth() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val postKey = EntityKey("Post", "1")
        val authorKey = EntityKey("User", "1")
        val profileKey = EntityKey("Profile", "100")

        val profileRecord = mapOf(
            "id" to NormalizedValue.Scalar("100"),
            "bio" to NormalizedValue.Scalar("Engineer")
        )
        backend.setRecord(profileKey, profileRecord)

        val authorRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice"),
            "profile" to NormalizedValue.Ref(profileKey)
        )
        backend.setRecord(authorKey, authorRecord)

        val postRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "title" to NormalizedValue.Scalar("Test Post"),
            "author" to NormalizedValue.Ref(authorKey)
        )
        backend.setRecord(postKey, postRecord)

        val profileAdapter = FakeEntityAdapter<TestProfile>(
            typeName = "Profile",
            idExtractor = { it.id },
            normalizer = { _, _ -> profileRecord to emptySet() },
            denormalizer = { record, _ ->
                TestProfile(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    bio = (record["bio"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> authorRecord to emptySet() },
            denormalizer = { record, ctx ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String,
                    profile = profileRef?.let { ctx.resolveReference(it.key) as? TestProfile }
                )
            }
        )

        val postAdapter = FakeEntityAdapter<TestPost>(
            typeName = "Post",
            idExtractor = { it.id },
            normalizer = { _, _ -> postRecord to emptySet() },
            denormalizer = { record, ctx ->
                val authorRef = record["author"] as? NormalizedValue.Ref
                TestPost(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    title = (record["title"] as NormalizedValue.Scalar).value as String,
                    author = authorRef?.let { ctx.resolveReference(it.key) as? TestUser }
                )
            }
        )

        val registry = SchemaRegistry(mapOf(
            "Post" to postAdapter,
            "User" to userAdapter,
            "Profile" to profileAdapter
        ))

        val shape = FakeShape<TestPost>(
            id = ShapeId("PostShape"),
            outputType = TestPost::class,
            edgeFields = setOf("author"),
            maxDepth = 1, // Only go 1 level deep
            refExtractor = { record ->
                buildSet {
                    (record["author"] as? NormalizedValue.Ref)?.let { add(it.key) }
                    (record["profile"] as? NormalizedValue.Ref)?.let { add(it.key) }
                }
            }
        )

        // When
        val result = composeFromRoot(postKey, shape, registry, backend)

        // Then
        assertEquals("Test Post", result.value.title)
        // Should only include post (depth >= 1 prevents author from being added)
        assertEquals(setOf(postKey), result.dependencies)
    }

    @Test
    fun composeFromRoot_givenBackendReadThrowsException_whenCompose_thenHandlesGracefullyAndContinues() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        backend.shouldThrowOnRead = true
        val userKey = EntityKey("User", "1")

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> emptyMap<String, NormalizedValue>() to emptySet() },
            denormalizer = { _, _ -> TestUser("1", "Alice") }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = emptySet(),
            refExtractor = { emptySet() }
        )

        // When / Then
        val exception = assertFailsWith<GraphCompositionException> {
            composeFromRoot(userKey, shape, registry, backend)
        }

        assertEquals(userKey, exception.rootKey)
        assertTrue(exception.message?.contains("Root entity not found") == true)
    }

    @Test
    fun composeFromRoot_givenRootRecordNotFound_whenCompose_thenThrowsGraphCompositionException() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "999") // Non-existent

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> emptyMap<String, NormalizedValue>() to emptySet() },
            denormalizer = { _, _ -> TestUser("999", "Nobody") }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = emptySet(),
            refExtractor = { emptySet() }
        )

        // When / Then
        val exception = assertFailsWith<GraphCompositionException> {
            composeFromRoot(userKey, shape, registry, backend)
        }

        assertEquals(userKey, exception.rootKey)
        assertTrue(exception.message?.contains("Root entity not found") == true)
    }

    @Test
    fun composeFromRoot_givenDenormalizationThrows_whenCompose_thenThrowsGraphCompositionException() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")
        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice")
        )
        backend.setRecord(userKey, userRecord)

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { _, _ ->
                throw IllegalStateException("Denormalization failed")
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = emptySet(),
            refExtractor = { emptySet() }
        )

        // When / Then
        val exception = assertFailsWith<GraphCompositionException> {
            composeFromRoot(userKey, shape, registry, backend)
        }

        assertEquals(userKey, exception.rootKey)
        assertTrue(exception.message?.contains("Failed to denormalize root entity") == true)
        assertNotNull(exception.cause)
        assertTrue(exception.cause is IllegalStateException)
    }

    @Test
    fun composeFromRoot_givenAllEntitiesHaveMeta_whenCompose_thenAggregatesMetaCorrectly() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")
        val profileKey = EntityKey("Profile", "100")

        val now = Clock.System.now()
        val userMeta = EntityMeta(etag = "user-etag", updatedAt = now)
        val profileMeta = EntityMeta(etag = "profile-etag", updatedAt = now)

        backend.setMeta(userKey, userMeta)
        backend.setMeta(profileKey, profileMeta)

        val profileRecord = mapOf(
            "id" to NormalizedValue.Scalar("100"),
            "bio" to NormalizedValue.Scalar("Engineer")
        )
        backend.setRecord(profileKey, profileRecord)

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice"),
            "profile" to NormalizedValue.Ref(profileKey)
        )
        backend.setRecord(userKey, userRecord)

        val profileAdapter = FakeEntityAdapter<TestProfile>(
            typeName = "Profile",
            idExtractor = { it.id },
            normalizer = { _, _ -> profileRecord to emptySet() },
            denormalizer = { record, _ ->
                TestProfile(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    bio = (record["bio"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, ctx ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String,
                    profile = profileRef?.let { ctx.resolveReference(it.key) as? TestProfile }
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter, "Profile" to profileAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile"),
            refExtractor = { record ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                setOfNotNull(profileRef?.key)
            }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertEquals(now, result.meta.updatedAt)
        assertNotNull(result.meta.etagFingerprint)
    }

    @Test
    fun composeFromRoot_givenSomeEntitiesHaveNullMeta_whenCompose_thenUsesCurrentTime() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice")
        )
        backend.setRecord(userKey, userRecord)
        // No metadata set for user

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
            edgeFields = emptySet(),
            refExtractor = { emptySet() }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertNotNull(result.meta.updatedAt)
        // Since no meta, etagFingerprint should be null
        assertNull(result.meta.etagFingerprint)
    }

    @Test
    fun composeFromRoot_givenNoEtags_whenCompose_thenEtagFingerprintIsNull() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")

        val now = Clock.System.now()
        val userMeta = EntityMeta(etag = null, updatedAt = now)
        backend.setMeta(userKey, userMeta)

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice")
        )
        backend.setRecord(userKey, userRecord)

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
            edgeFields = emptySet(),
            refExtractor = { emptySet() }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertEquals(now, result.meta.updatedAt)
        assertNull(result.meta.etagFingerprint)
    }

    @Test
    fun composeFromRoot_givenMultipleEtags_whenCompose_thenEtagsSortedAndConcatenated() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")
        val profileKey = EntityKey("Profile", "100")

        val now = Clock.System.now()
        val userMeta = EntityMeta(etag = "zebra-etag", updatedAt = now)
        val profileMeta = EntityMeta(etag = "alpha-etag", updatedAt = now)

        backend.setMeta(userKey, userMeta)
        backend.setMeta(profileKey, profileMeta)

        val profileRecord = mapOf(
            "id" to NormalizedValue.Scalar("100"),
            "bio" to NormalizedValue.Scalar("Engineer")
        )
        backend.setRecord(profileKey, profileRecord)

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice"),
            "profile" to NormalizedValue.Ref(profileKey)
        )
        backend.setRecord(userKey, userRecord)

        val profileAdapter = FakeEntityAdapter<TestProfile>(
            typeName = "Profile",
            idExtractor = { it.id },
            normalizer = { _, _ -> profileRecord to emptySet() },
            denormalizer = { record, _ ->
                TestProfile(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    bio = (record["bio"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, ctx ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String,
                    profile = profileRef?.let { ctx.resolveReference(it.key) as? TestProfile }
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter, "Profile" to profileAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile"),
            refExtractor = { record ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                setOfNotNull(profileRef?.key)
            }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertNotNull(result.meta.etagFingerprint)
        // ETags should be sorted: "alpha-etag|zebra-etag"
    }

    @Test
    fun composeFromRoot_givenResolveReferenceUsesCache_whenCompose_thenSucceeds() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")
        val profileKey = EntityKey("Profile", "100")

        val profileRecord = mapOf(
            "id" to NormalizedValue.Scalar("100"),
            "bio" to NormalizedValue.Scalar("Engineer")
        )
        backend.setRecord(profileKey, profileRecord)

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice"),
            "profile" to NormalizedValue.Ref(profileKey)
        )
        backend.setRecord(userKey, userRecord)

        val profileAdapter = FakeEntityAdapter<TestProfile>(
            typeName = "Profile",
            idExtractor = { it.id },
            normalizer = { _, _ -> profileRecord to emptySet() },
            denormalizer = { record, _ ->
                TestProfile(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    bio = (record["bio"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, ctx ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String,
                    profile = profileRef?.let { ctx.resolveReference(it.key) as? TestProfile }
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter, "Profile" to profileAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile"),
            refExtractor = { record ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                setOfNotNull(profileRef?.key)
            }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertEquals("Alice", result.value.name)
        assertEquals("Engineer", result.value.profile?.bio)
        // Verify BFS batching occurred (2 calls: one for root, one for profile reference)
        assertEquals(2, backend.readCallCount)
    }

    @Test
    fun composeFromRoot_givenResolveReferenceFetchesFromBackend_whenCompose_thenSucceeds() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val postKey = EntityKey("Post", "1")
        val authorKey = EntityKey("User", "1")
        val comment1Key = EntityKey("Comment", "c1")

        // Only set post and author in backend initially
        val authorRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice")
        )
        backend.setRecord(authorKey, authorRecord)

        val postRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "title" to NormalizedValue.Scalar("Test Post"),
            "author" to NormalizedValue.Ref(authorKey)
        )
        backend.setRecord(postKey, postRecord)

        // Comment will be fetched during denormalization by resolveReference
        val comment1Record = mapOf(
            "id" to NormalizedValue.Scalar("c1"),
            "text" to NormalizedValue.Scalar("Great post!")
        )
        backend.setRecord(comment1Key, comment1Record)

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> authorRecord to emptySet() },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val commentAdapter = FakeEntityAdapter<TestComment>(
            typeName = "Comment",
            idExtractor = { it.id },
            normalizer = { _, _ -> comment1Record to emptySet() },
            denormalizer = { record, _ ->
                TestComment(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    text = (record["text"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val postAdapter = FakeEntityAdapter<TestPost>(
            typeName = "Post",
            idExtractor = { it.id },
            normalizer = { _, _ -> postRecord to emptySet() },
            denormalizer = { record, ctx ->
                val authorRef = record["author"] as? NormalizedValue.Ref
                // Simulate accessing a comment via resolveReference during denormalization
                val comment = ctx.resolveReference(comment1Key) as? TestComment
                TestPost(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    title = (record["title"] as NormalizedValue.Scalar).value as String,
                    author = authorRef?.let { ctx.resolveReference(it.key) as? TestUser },
                    comments = listOfNotNull(comment)
                )
            }
        )

        val registry = SchemaRegistry(mapOf(
            "Post" to postAdapter,
            "User" to userAdapter,
            "Comment" to commentAdapter
        ))

        val shape = FakeShape<TestPost>(
            id = ShapeId("PostShape"),
            outputType = TestPost::class,
            edgeFields = setOf("author"),
            refExtractor = { record ->
                val authorRef = record["author"] as? NormalizedValue.Ref
                setOfNotNull(authorRef?.key)
            }
        )

        // When
        val result = composeFromRoot(postKey, shape, registry, backend)

        // Then
        assertEquals("Test Post", result.value.title)
        assertEquals("Alice", result.value.author?.name)
        assertEquals(1, result.value.comments.size)
        assertEquals("Great post!", result.value.comments[0].text)
    }

    @Test
    fun composeFromRoot_givenLargeGraph_whenCompose_thenBatchesReadsCorrectly() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val rootKey = EntityKey("User", "root")

        // Create 300 entities to test batching (> 256)
        val childKeys = (1..300).map { EntityKey("Comment", "c$it") }

        // Set up root record with references to all children
        val rootRecord = mapOf(
            "id" to NormalizedValue.Scalar("root"),
            "name" to NormalizedValue.Scalar("Root User"),
            "comments" to NormalizedValue.RefList(childKeys)
        )
        backend.setRecord(rootKey, rootRecord)

        // Set up child records
        childKeys.forEach { key ->
            val commentRecord = mapOf(
                "id" to NormalizedValue.Scalar(key.id),
                "text" to NormalizedValue.Scalar("Comment ${key.id}")
            )
            backend.setRecord(key, commentRecord)
        }

        val commentAdapter = FakeEntityAdapter<TestComment>(
            typeName = "Comment",
            idExtractor = { it.id },
            normalizer = { _, _ -> emptyMap<String, NormalizedValue>() to emptySet() },
            denormalizer = { record, _ ->
                TestComment(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    text = (record["text"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> rootRecord to emptySet() },
            denormalizer = { record, _ ->
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val registry = SchemaRegistry(mapOf(
            "User" to userAdapter,
            "Comment" to commentAdapter
        ))

        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = setOf("comments"),
            refExtractor = { record ->
                val commentRefs = record["comments"] as? NormalizedValue.RefList
                commentRefs?.keys?.toSet() ?: emptySet()
            }
        )

        // When
        val result = composeFromRoot(rootKey, shape, registry, backend)

        // Then
        assertEquals("Root User", result.value.name)
        assertEquals(301, result.dependencies.size) // root + 300 comments
        // Verify batching: should have made 3 read calls (1 for root, 256 for first batch, 44 for second batch)
        assertTrue(backend.readCallCount >= 2)
    }

    @Test
    fun dataClass_givenComposeResult_whenCopy_thenCreatesCopyWithChanges() {
        // Given
        val original = ComposeResult(
            value = "test",
            dependencies = setOf(EntityKey("User", "1")),
            meta = GraphMeta(updatedAt = Clock.System.now())
        )

        // When
        val copied = original.copy(value = "updated")

        // Then
        assertEquals("updated", copied.value)
        assertEquals(original.dependencies, copied.dependencies)
        assertEquals(original.meta, copied.meta)
    }

    @Test
    fun dataClass_givenTwoComposeResultsWithSameValues_whenCompare_thenAreEqual() {
        // Given
        val timestamp = Clock.System.now()
        val deps = setOf(EntityKey("User", "1"))
        val meta = GraphMeta(updatedAt = timestamp, etagFingerprint = "abc")

        val result1 = ComposeResult(value = "test", dependencies = deps, meta = meta)
        val result2 = ComposeResult(value = "test", dependencies = deps, meta = meta)

        // When / Then
        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun composeFromRoot_givenEmptyOutboundRefs_whenCompose_thenReturnsSingleDependency() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice")
        )
        backend.setRecord(userKey, userRecord)

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
            edgeFields = emptySet(),
            refExtractor = { emptySet() } // No outbound refs
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertEquals("Alice", result.value.name)
        assertEquals(setOf(userKey), result.dependencies)
    }

    @Test
    fun composeFromRoot_givenMinUpdatedAt_whenCompose_thenUsesMinimumTimestamp() = runTest {
        // Given
        val backend = FakeNormalizationBackend()
        val userKey = EntityKey("User", "1")
        val profileKey = EntityKey("Profile", "100")

        val olderTime = Instant.parse("2024-01-01T00:00:00Z")
        val newerTime = Instant.parse("2024-12-01T00:00:00Z")

        val userMeta = EntityMeta(etag = "user-etag", updatedAt = newerTime)
        val profileMeta = EntityMeta(etag = "profile-etag", updatedAt = olderTime)

        backend.setMeta(userKey, userMeta)
        backend.setMeta(profileKey, profileMeta)

        val profileRecord = mapOf(
            "id" to NormalizedValue.Scalar("100"),
            "bio" to NormalizedValue.Scalar("Engineer")
        )
        backend.setRecord(profileKey, profileRecord)

        val userRecord = mapOf(
            "id" to NormalizedValue.Scalar("1"),
            "name" to NormalizedValue.Scalar("Alice"),
            "profile" to NormalizedValue.Ref(profileKey)
        )
        backend.setRecord(userKey, userRecord)

        val profileAdapter = FakeEntityAdapter<TestProfile>(
            typeName = "Profile",
            idExtractor = { it.id },
            normalizer = { _, _ -> profileRecord to emptySet() },
            denormalizer = { record, _ ->
                TestProfile(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    bio = (record["bio"] as NormalizedValue.Scalar).value as String
                )
            }
        )

        val userAdapter = FakeEntityAdapter<TestUser>(
            typeName = "User",
            idExtractor = { it.id },
            normalizer = { _, _ -> userRecord to emptySet() },
            denormalizer = { record, ctx ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String,
                    profile = profileRef?.let { ctx.resolveReference(it.key) as? TestProfile }
                )
            }
        )

        val registry = SchemaRegistry(mapOf("User" to userAdapter, "Profile" to profileAdapter))
        val shape = FakeShape<TestUser>(
            id = ShapeId("UserShape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile"),
            refExtractor = { record ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                setOfNotNull(profileRef?.key)
            }
        )

        // When
        val result = composeFromRoot(userKey, shape, registry, backend)

        // Then
        assertEquals(olderTime, result.meta.updatedAt)
    }
}
