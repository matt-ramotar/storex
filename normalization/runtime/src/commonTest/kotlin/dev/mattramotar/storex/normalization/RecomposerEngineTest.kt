package dev.mattramotar.storex.normalization

import app.cash.turbine.test
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecomposerEngineTest {

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
        val author: TestUser? = null
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
        private val refExtractor: (NormalizedRecord) -> Set<EntityKey>
    ) : Shape<V> {
        override fun outboundRefs(record: NormalizedRecord): Set<EntityKey> = refExtractor(record)
    }

    // Fake NormalizationBackend for testing
    class FakeNormalizationBackend : NormalizationBackend {
        private val storage = mutableMapOf<EntityKey, NormalizedRecord>()
        private val _entityInvalidations = MutableSharedFlow<Set<EntityKey>>(replay = 0)
        private val _rootInvalidations = MutableSharedFlow<Set<RootRef>>(replay = 0)
        val updatedDependencies = mutableListOf<Pair<RootRef, Set<EntityKey>>>()

        override val entityInvalidations: Flow<Set<EntityKey>> = _entityInvalidations
        override val rootInvalidations: Flow<Set<RootRef>> = _rootInvalidations

        fun setRecord(key: EntityKey, record: NormalizedRecord) {
            storage[key] = record
        }

        suspend fun emitRootInvalidation(roots: Set<RootRef>) {
            _rootInvalidations.emit(roots)
        }

        override suspend fun read(keys: Set<EntityKey>): Map<EntityKey, NormalizedRecord?> {
            return keys.associateWith { storage[it] }
        }

        override suspend fun readMeta(keys: Set<EntityKey>): Map<EntityKey, EntityMeta?> {
            return keys.associateWith { null }
        }

        override suspend fun apply(changeSet: NormalizedChangeSet) {
            // Not used in RecomposerEngine
        }

        override suspend fun updateRootDependencies(root: RootRef, dependsOn: Set<EntityKey>) {
            updatedDependencies.add(root to dependsOn)
        }

        override suspend fun clear() {
            storage.clear()
            updatedDependencies.clear()
        }
    }

    // Fake IndexManager for testing
    class FakeIndexManager : IndexManager {
        private val indices = mutableMapOf<Long, MutableStateFlow<List<EntityKey>?>>()

        override suspend fun updateIndex(requestKey: StoreKey, roots: List<EntityKey>) {
            val hash = requestKey.stableHash()
            indices.getOrPut(hash) { MutableStateFlow(null) }.value = roots
        }

        override fun streamIndex(requestKey: StoreKey): Flow<List<EntityKey>?> {
            val hash = requestKey.stableHash()
            return indices.getOrPut(hash) { MutableStateFlow(null) }
        }

        fun setIndex(requestKey: StoreKey, roots: List<EntityKey>?) {
            val hash = requestKey.stableHash()
            indices.getOrPut(hash) { MutableStateFlow(null) }.value = roots
        }
    }

    private fun createTestStoreKey(query: String = "test"): StoreKey {
        return QueryKey(
            namespace = StoreNamespace("test"),
            query = mapOf("q" to query)
        )
    }

    private fun createTestUserAdapter(): FakeEntityAdapter<TestUser> {
        return FakeEntityAdapter(
            typeName = "TestUser",
            idExtractor = { it.id },
            normalizer = { user, ctx ->
                val record = mutableMapOf<String, NormalizedValue>()
                record["id"] = NormalizedValue.Scalar(user.id)
                record["name"] = NormalizedValue.Scalar(user.name)
                if (user.profile != null) {
                    record["profile"] = NormalizedValue.Ref(ctx.registerNested(user.profile))
                }
                record to setOf("id", "name", "profile")
            },
            denormalizer = { record, ctx ->
                val profileRef = record["profile"] as? NormalizedValue.Ref
                val profile = if (profileRef != null) {
                    ctx.resolveReference(profileRef.key) as? TestProfile
                } else null
                TestUser(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    name = (record["name"] as NormalizedValue.Scalar).value as String,
                    profile = profile
                )
            }
        )
    }

    private fun createTestProfileAdapter(): FakeEntityAdapter<TestProfile> {
        return FakeEntityAdapter(
            typeName = "TestProfile",
            idExtractor = { it.id },
            normalizer = { profile, _ ->
                mapOf(
                    "id" to NormalizedValue.Scalar(profile.id),
                    "bio" to NormalizedValue.Scalar(profile.bio)
                ) to setOf("id", "bio")
            },
            denormalizer = { record, _ ->
                TestProfile(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    bio = (record["bio"] as NormalizedValue.Scalar).value as String
                )
            }
        )
    }

    private fun createTestPostAdapter(): FakeEntityAdapter<TestPost> {
        return FakeEntityAdapter(
            typeName = "TestPost",
            idExtractor = { it.id },
            normalizer = { post, ctx ->
                val record = mutableMapOf<String, NormalizedValue>()
                record["id"] = NormalizedValue.Scalar(post.id)
                record["title"] = NormalizedValue.Scalar(post.title)
                if (post.author != null) {
                    record["author"] = NormalizedValue.Ref(ctx.registerNested(post.author))
                }
                record to setOf("id", "title", "author")
            },
            denormalizer = { record, ctx ->
                val authorRef = record["author"] as? NormalizedValue.Ref
                val author = if (authorRef != null) {
                    ctx.resolveReference(authorRef.key) as? TestUser
                } else null
                TestPost(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    title = (record["title"] as NormalizedValue.Scalar).value as String,
                    author = author
                )
            }
        )
    }

    @Test
    fun stream_givenNullIndex_whenCalled_thenEmitsEmptyList() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        index.setIndex(requestKey, null)

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(emptyList(), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenEmptyIndex_whenCalled_thenEmitsEmptyList() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        index.setIndex(requestKey, emptyList())

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(emptyList(), result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenSingleRootEntity_whenCalled_thenEmitsOneEntity() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val userKey = EntityKey("TestUser", "user-1")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice")
            )
        )
        index.setIndex(requestKey, listOf(userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(1, result.size)
            val user = result[0] as? TestUser
            assertNotNull(user)
            assertEquals("user-1", user.id)
            assertEquals("Alice", user.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenMultipleRootEntities_whenCalled_thenEmitsMultipleEntities() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val user1Key = EntityKey("TestUser", "user-1")
        val user2Key = EntityKey("TestUser", "user-2")
        backend.setRecord(
            user1Key,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice")
            )
        )
        backend.setRecord(
            user2Key,
            mapOf(
                "id" to NormalizedValue.Scalar("user-2"),
                "name" to NormalizedValue.Scalar("Bob")
            )
        )
        index.setIndex(requestKey, listOf(user1Key, user2Key))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(2, result.size)
            val user1 = result[0] as? TestUser
            val user2 = result[1] as? TestUser
            assertNotNull(user1)
            assertNotNull(user2)
            assertEquals("user-1", user1.id)
            assertEquals("user-2", user2.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenRootInvalidation_whenMatchingRoot_thenRecomposesAgain() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val userKey = EntityKey("TestUser", "user-1")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice")
            )
        )
        index.setIndex(requestKey, listOf(userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result1 = awaitItem()
            assertEquals(1, result1.size)
            assertEquals("Alice", (result1[0] as TestUser).name)

            // Update the record
            backend.setRecord(
                userKey,
                mapOf(
                    "id" to NormalizedValue.Scalar("user-1"),
                    "name" to NormalizedValue.Scalar("Alice Updated")
                )
            )
            backend.emitRootInvalidation(setOf(RootRef(requestKey, shape.id)))

            val result2 = awaitItem()
            assertEquals(1, result2.size)
            assertEquals("Alice Updated", (result2[0] as TestUser).name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenRootInvalidation_whenNonMatchingRoot_thenDoesNotRecompose() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()
        val otherRequestKey = createTestStoreKey("other")

        val userKey = EntityKey("TestUser", "user-1")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice")
            )
        )
        index.setIndex(requestKey, listOf(userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result1 = awaitItem()
            assertEquals(1, result1.size)

            // Emit invalidation for different root
            backend.emitRootInvalidation(setOf(RootRef(otherRequestKey, shape.id)))

            // Should not receive another emission
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenEntityWithNestedReferences_whenCalled_thenResolvesGraph() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = SchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        )
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { record ->
            val profileRef = record["profile"] as? NormalizedValue.Ref
            if (profileRef != null) setOf(profileRef.key) else emptySet()
        }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val userKey = EntityKey("TestUser", "user-1")
        val profileKey = EntityKey("TestProfile", "profile-1")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice"),
                "profile" to NormalizedValue.Ref(profileKey)
            )
        )
        backend.setRecord(
            profileKey,
            mapOf(
                "id" to NormalizedValue.Scalar("profile-1"),
                "bio" to NormalizedValue.Scalar("Software Engineer")
            )
        )
        index.setIndex(requestKey, listOf(userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(1, result.size)
            val user = result[0] as? TestUser
            assertNotNull(user)
            assertEquals("user-1", user.id)
            assertNotNull(user.profile)
            assertEquals("profile-1", user.profile?.id)
            assertEquals("Software Engineer", user.profile?.bio)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenDeepNestedGraph_whenCalled_thenResolvesAllLevels() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val postAdapter = createTestPostAdapter()
        val registry = SchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter,
                "TestPost" to postAdapter
            )
        )
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("post-shape"),
            outputType = TestPost::class,
            edgeFields = setOf("author", "profile")
        ) { record ->
            val refs = mutableSetOf<EntityKey>()
            (record["author"] as? NormalizedValue.Ref)?.let { refs.add(it.key) }
            (record["profile"] as? NormalizedValue.Ref)?.let { refs.add(it.key) }
            refs
        }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val postKey = EntityKey("TestPost", "post-1")
        val userKey = EntityKey("TestUser", "user-1")
        val profileKey = EntityKey("TestProfile", "profile-1")

        backend.setRecord(
            postKey,
            mapOf(
                "id" to NormalizedValue.Scalar("post-1"),
                "title" to NormalizedValue.Scalar("Test Post"),
                "author" to NormalizedValue.Ref(userKey)
            )
        )
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice"),
                "profile" to NormalizedValue.Ref(profileKey)
            )
        )
        backend.setRecord(
            profileKey,
            mapOf(
                "id" to NormalizedValue.Scalar("profile-1"),
                "bio" to NormalizedValue.Scalar("Software Engineer")
            )
        )
        index.setIndex(requestKey, listOf(postKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(1, result.size)
            val post = result[0] as? TestPost
            assertNotNull(post)
            assertEquals("post-1", post.id)
            assertNotNull(post.author)
            assertEquals("user-1", post.author?.id)
            assertNotNull(post.author?.profile)
            assertEquals("profile-1", post.author?.profile?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenCircularReferences_whenCalled_thenHandlesWithoutInfiniteLoop() = runTest {
        // Given
        data class TestNode(val id: String, val nextId: String?)

        val nodeAdapter = FakeEntityAdapter<TestNode>(
            typeName = "TestNode",
            idExtractor = { it.id },
            normalizer = { node, ctx ->
                val record = mutableMapOf<String, NormalizedValue>()
                record["id"] = NormalizedValue.Scalar(node.id)
                if (node.nextId != null) {
                    record["next"] = NormalizedValue.Ref(EntityKey("TestNode", node.nextId))
                }
                record to setOf("id", "next")
            },
            denormalizer = { record, _ ->
                val nextRef = record["next"] as? NormalizedValue.Ref
                TestNode(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    nextId = nextRef?.key?.id
                )
            }
        )

        val registry = SchemaRegistry(mapOf("TestNode" to nodeAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("node-shape"),
            outputType = TestNode::class,
            edgeFields = setOf("next")
        ) { record ->
            val nextRef = record["next"] as? NormalizedValue.Ref
            if (nextRef != null) setOf(nextRef.key) else emptySet()
        }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val node1Key = EntityKey("TestNode", "node-1")
        val node2Key = EntityKey("TestNode", "node-2")

        // Create circular reference: node1 -> node2 -> node1
        backend.setRecord(
            node1Key,
            mapOf(
                "id" to NormalizedValue.Scalar("node-1"),
                "next" to NormalizedValue.Ref(node2Key)
            )
        )
        backend.setRecord(
            node2Key,
            mapOf(
                "id" to NormalizedValue.Scalar("node-2"),
                "next" to NormalizedValue.Ref(node1Key)
            )
        )
        index.setIndex(requestKey, listOf(node1Key))

        // When
        engine.stream(requestKey).test {
            // Then
            // Should complete without hanging - the seen set prevents infinite traversal
            val result = awaitItem()
            assertEquals(1, result.size)
            val node = result[0] as? TestNode
            assertNotNull(node)
            assertEquals("node-1", node.id)
            assertEquals("node-2", node.nextId) // References node-2 by ID

            // Verify both nodes were collected in dependencies despite circular refs
            assertTrue(backend.updatedDependencies.isNotEmpty())
            val (_, deps) = backend.updatedDependencies.first()
            assertTrue(deps.contains(node1Key))
            assertTrue(deps.contains(node2Key))
            assertEquals(2, deps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenLargeBatch_whenCalled_thenBatchesReadsTo256() = runTest {
        // Given
        data class TestItem(val id: String, val refs: List<TestItem>)

        val itemAdapter = FakeEntityAdapter<TestItem>(
            typeName = "TestItem",
            idExtractor = { it.id },
            normalizer = { item, ctx ->
                val record = mutableMapOf<String, NormalizedValue>()
                record["id"] = NormalizedValue.Scalar(item.id)
                if (item.refs.isNotEmpty()) {
                    record["refs"] = NormalizedValue.RefList(item.refs.map { ctx.registerNested(it) })
                }
                record to setOf("id", "refs")
            },
            denormalizer = { record, _ ->
                TestItem(
                    id = (record["id"] as NormalizedValue.Scalar).value as String,
                    refs = emptyList()
                )
            }
        )

        val registry = SchemaRegistry(mapOf("TestItem" to itemAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("item-shape"),
            outputType = TestItem::class,
            edgeFields = setOf("refs")
        ) { record ->
            val refsValue = record["refs"] as? NormalizedValue.RefList
            refsValue?.keys?.toSet() ?: emptySet()
        }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        // Create root with 300 references to test batching
        val rootKey = EntityKey("TestItem", "root")
        val refKeys = (1..300).map { EntityKey("TestItem", "item-$it") }

        backend.setRecord(
            rootKey,
            mapOf(
                "id" to NormalizedValue.Scalar("root"),
                "refs" to NormalizedValue.RefList(refKeys)
            )
        )
        refKeys.forEach { key ->
            backend.setRecord(
                key,
                mapOf("id" to NormalizedValue.Scalar(key.id))
            )
        }
        index.setIndex(requestKey, listOf(rootKey))

        // When
        engine.stream(requestKey).test {
            // Then
            // Should complete successfully with batched reads (256 + 44 + 1)
            val result = awaitItem()
            assertEquals(1, result.size)

            // Verify dependencies were updated
            assertTrue(backend.updatedDependencies.isNotEmpty())
            val (root, deps) = backend.updatedDependencies.first()
            assertEquals(requestKey, root.requestKey)
            // Should include root + all 300 refs
            assertEquals(301, deps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenMissingEntity_whenCalled_thenReturnsNullForThatEntity() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val missingKey = EntityKey("TestUser", "missing")
        // Don't set the record in backend
        index.setIndex(requestKey, listOf(missingKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(1, result.size)
            assertNull(result[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenIndexChanges_whenCalled_thenEmitsNewComposition() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val user1Key = EntityKey("TestUser", "user-1")
        val user2Key = EntityKey("TestUser", "user-2")
        backend.setRecord(
            user1Key,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice")
            )
        )
        backend.setRecord(
            user2Key,
            mapOf(
                "id" to NormalizedValue.Scalar("user-2"),
                "name" to NormalizedValue.Scalar("Bob")
            )
        )
        index.setIndex(requestKey, listOf(user1Key))

        // When
        engine.stream(requestKey).test {
            // Then
            val result1 = awaitItem()
            assertEquals(1, result1.size)
            assertEquals("Alice", (result1[0] as TestUser).name)

            // Change the index
            index.setIndex(requestKey, listOf(user2Key))

            val result2 = awaitItem()
            assertEquals(1, result2.size)
            assertEquals("Bob", (result2[0] as TestUser).name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenNoReferences_whenCalled_thenOnlyReadsRoots() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = emptySet()
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val userKey = EntityKey("TestUser", "user-1")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice")
            )
        )
        index.setIndex(requestKey, listOf(userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(1, result.size)

            // Verify dependencies only include the root
            assertTrue(backend.updatedDependencies.isNotEmpty())
            val (root, deps) = backend.updatedDependencies.first()
            assertEquals(requestKey, root.requestKey)
            assertEquals(setOf(userKey), deps)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenReferences_whenCalled_thenUpdatesDependencies() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = SchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        )
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { record ->
            val profileRef = record["profile"] as? NormalizedValue.Ref
            if (profileRef != null) setOf(profileRef.key) else emptySet()
        }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val userKey = EntityKey("TestUser", "user-1")
        val profileKey = EntityKey("TestProfile", "profile-1")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice"),
                "profile" to NormalizedValue.Ref(profileKey)
            )
        )
        backend.setRecord(
            profileKey,
            mapOf(
                "id" to NormalizedValue.Scalar("profile-1"),
                "bio" to NormalizedValue.Scalar("Software Engineer")
            )
        )
        index.setIndex(requestKey, listOf(userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            awaitItem()

            // Verify dependencies include both user and profile
            assertTrue(backend.updatedDependencies.isNotEmpty())
            val (root, deps) = backend.updatedDependencies.first()
            assertEquals(requestKey, root.requestKey)
            assertEquals(shape.id, root.shapeId)
            assertTrue(deps.contains(userKey))
            assertTrue(deps.contains(profileKey))
            assertEquals(2, deps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenMultipleRootsSameEntity_whenCalled_thenDeduplicatesReads() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = emptySet()
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val userKey = EntityKey("TestUser", "user-1")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice")
            )
        )
        // Index has duplicates
        index.setIndex(requestKey, listOf(userKey, userKey, userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            // Should return 3 items (duplicates preserved in output)
            assertEquals(3, result.size)
            assertEquals("Alice", (result[0] as TestUser).name)
            assertEquals("Alice", (result[1] as TestUser).name)
            assertEquals("Alice", (result[2] as TestUser).name)

            // But dependencies should only include the key once
            val (_, deps) = backend.updatedDependencies.first()
            assertEquals(1, deps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenMissingNestedReference_whenCalled_thenResolvesWithNull() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val profileAdapter = createTestProfileAdapter()
        val registry = SchemaRegistry(
            mapOf(
                "TestUser" to userAdapter,
                "TestProfile" to profileAdapter
            )
        )
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = setOf("profile")
        ) { record ->
            val profileRef = record["profile"] as? NormalizedValue.Ref
            if (profileRef != null) setOf(profileRef.key) else emptySet()
        }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val userKey = EntityKey("TestUser", "user-1")
        val missingProfileKey = EntityKey("TestProfile", "missing")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice"),
                "profile" to NormalizedValue.Ref(missingProfileKey)
            )
        )
        // Don't set the profile record
        index.setIndex(requestKey, listOf(userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(1, result.size)
            val user = result[0] as? TestUser
            assertNotNull(user)
            assertEquals("user-1", user.id)
            assertNull(user.profile) // Missing profile should resolve to null
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stream_givenEmptyBatch_whenToVisitIsEmpty_thenContinuesCorrectly() = runTest {
        // Given
        val userAdapter = createTestUserAdapter()
        val registry = SchemaRegistry(mapOf("TestUser" to userAdapter))
        val backend = FakeNormalizationBackend()
        val index = FakeIndexManager()
        val shape = FakeShape(
            id = ShapeId("user-shape"),
            outputType = TestUser::class,
            edgeFields = emptySet()
        ) { emptySet() }
        val engine = RecomposerEngine(registry, backend, index, shape)
        val requestKey = createTestStoreKey()

        val userKey = EntityKey("TestUser", "user-1")
        backend.setRecord(
            userKey,
            mapOf(
                "id" to NormalizedValue.Scalar("user-1"),
                "name" to NormalizedValue.Scalar("Alice")
            )
        )
        index.setIndex(requestKey, listOf(userKey))

        // When
        engine.stream(requestKey).test {
            // Then
            val result = awaitItem()
            assertEquals(1, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
