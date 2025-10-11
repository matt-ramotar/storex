package dev.mattramotar.storex.normalization.backend

import dev.mattramotar.storex.core.ByIdKey
import dev.mattramotar.storex.core.EntityId
import dev.mattramotar.storex.core.QueryKey
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreNamespace
import dev.mattramotar.storex.normalization.ShapeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RootRefTest {

    // ===== Helper Functions =====

    private fun createByIdKey(
        namespace: String = "users",
        type: String = "User",
        id: String = "123"
    ): ByIdKey = ByIdKey(
        namespace = StoreNamespace(namespace),
        entity = EntityId(type, id)
    )

    private fun createQueryKey(
        namespace: String = "posts",
        query: Map<String, String> = mapOf("filter" to "active")
    ): QueryKey = QueryKey(
        namespace = StoreNamespace(namespace),
        query = query
    )

    private fun createShapeId(value: String = "shape1"): ShapeId = ShapeId(value)

    // ===== Constructor Tests =====

    @Test
    fun constructor_givenValidByIdKey_whenCreated_thenPropertiesSet() {
        // Given
        val requestKey = createByIdKey()
        val shapeId = createShapeId()

        // When
        val rootRef = RootRef(requestKey, shapeId)

        // Then
        assertEquals(requestKey, rootRef.requestKey)
        assertEquals(shapeId, rootRef.shapeId)
    }

    @Test
    fun constructor_givenValidQueryKey_whenCreated_thenPropertiesSet() {
        // Given
        val requestKey = createQueryKey()
        val shapeId = createShapeId()

        // When
        val rootRef = RootRef(requestKey, shapeId)

        // Then
        assertEquals(requestKey, rootRef.requestKey)
        assertEquals(shapeId, rootRef.shapeId)
    }

    // ===== toString() Tests =====

    @Test
    fun toString_givenByIdKey_whenToString_thenReturnsFormattedString() {
        // Given
        val requestKey = createByIdKey(namespace = "users", type = "User", id = "123")
        val shapeId = createShapeId(value = "detailShape")
        val rootRef = RootRef(requestKey, shapeId)
        val expectedHash = requestKey.stableHash()

        // When
        val result = rootRef.toString()

        // Then
        assertEquals("$expectedHash:detailShape", result)
    }

    @Test
    fun toString_givenQueryKey_whenToString_thenReturnsFormattedString() {
        // Given
        val requestKey = createQueryKey(
            namespace = "posts",
            query = mapOf("status" to "published", "author" to "alice")
        )
        val shapeId = createShapeId(value = "listShape")
        val rootRef = RootRef(requestKey, shapeId)
        val expectedHash = requestKey.stableHash()

        // When
        val result = rootRef.toString()

        // Then
        assertEquals("$expectedHash:listShape", result)
    }

    @Test
    fun toString_givenEmptyQueryMap_whenToString_thenReturnsFormattedString() {
        // Given
        val requestKey = createQueryKey(query = emptyMap())
        val shapeId = createShapeId(value = "emptyQueryShape")
        val rootRef = RootRef(requestKey, shapeId)
        val expectedHash = requestKey.stableHash()

        // When
        val result = rootRef.toString()

        // Then
        assertEquals("$expectedHash:emptyQueryShape", result)
    }

    // ===== equals() Tests =====

    @Test
    fun equals_givenSameValues_whenCompared_thenReturnsTrue() {
        // Given
        val requestKey = createByIdKey()
        val shapeId = createShapeId()
        val rootRef1 = RootRef(requestKey, shapeId)
        val rootRef2 = RootRef(requestKey, shapeId)

        // When
        val result = rootRef1 == rootRef2

        // Then
        assertEquals(rootRef1, rootRef2)
        assertEquals(true, result)
    }

    @Test
    fun equals_givenDifferentRequestKey_whenCompared_thenReturnsFalse() {
        // Given
        val requestKey1 = createByIdKey(id = "123")
        val requestKey2 = createByIdKey(id = "456")
        val shapeId = createShapeId()
        val rootRef1 = RootRef(requestKey1, shapeId)
        val rootRef2 = RootRef(requestKey2, shapeId)

        // When
        val result = rootRef1 == rootRef2

        // Then
        assertNotEquals(rootRef1, rootRef2)
        assertEquals(false, result)
    }

    @Test
    fun equals_givenDifferentShapeId_whenCompared_thenReturnsFalse() {
        // Given
        val requestKey = createByIdKey()
        val shapeId1 = createShapeId(value = "shape1")
        val shapeId2 = createShapeId(value = "shape2")
        val rootRef1 = RootRef(requestKey, shapeId1)
        val rootRef2 = RootRef(requestKey, shapeId2)

        // When
        val result = rootRef1 == rootRef2

        // Then
        assertNotEquals(rootRef1, rootRef2)
        assertEquals(false, result)
    }

    @Test
    fun equals_givenBothDifferent_whenCompared_thenReturnsFalse() {
        // Given
        val requestKey1 = createByIdKey(id = "123")
        val requestKey2 = createQueryKey()
        val shapeId1 = createShapeId(value = "shape1")
        val shapeId2 = createShapeId(value = "shape2")
        val rootRef1 = RootRef(requestKey1, shapeId1)
        val rootRef2 = RootRef(requestKey2, shapeId2)

        // When
        val result = rootRef1 == rootRef2

        // Then
        assertNotEquals(rootRef1, rootRef2)
        assertEquals(false, result)
    }

    // ===== hashCode() Tests =====

    @Test
    fun hashCode_givenSameValues_whenHashCode_thenReturnsSameHash() {
        // Given
        val requestKey = createByIdKey()
        val shapeId = createShapeId()
        val rootRef1 = RootRef(requestKey, shapeId)
        val rootRef2 = RootRef(requestKey, shapeId)

        // When
        val hash1 = rootRef1.hashCode()
        val hash2 = rootRef2.hashCode()

        // Then
        assertEquals(hash1, hash2)
    }

    @Test
    fun hashCode_givenDifferentRequestKey_whenHashCode_thenReturnsDifferentHash() {
        // Given
        val requestKey1 = createByIdKey(id = "123")
        val requestKey2 = createByIdKey(id = "456")
        val shapeId = createShapeId()
        val rootRef1 = RootRef(requestKey1, shapeId)
        val rootRef2 = RootRef(requestKey2, shapeId)

        // When
        val hash1 = rootRef1.hashCode()
        val hash2 = rootRef2.hashCode()

        // Then
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun hashCode_givenDifferentShapeId_whenHashCode_thenReturnsDifferentHash() {
        // Given
        val requestKey = createByIdKey()
        val shapeId1 = createShapeId(value = "shape1")
        val shapeId2 = createShapeId(value = "shape2")
        val rootRef1 = RootRef(requestKey, shapeId1)
        val rootRef2 = RootRef(requestKey, shapeId2)

        // When
        val hash1 = rootRef1.hashCode()
        val hash2 = rootRef2.hashCode()

        // Then
        assertNotEquals(hash1, hash2)
    }

    // ===== copy() Tests =====

    @Test
    fun copy_givenNoChanges_whenCopy_thenCreatesEqualInstance() {
        // Given
        val requestKey = createByIdKey()
        val shapeId = createShapeId()
        val original = RootRef(requestKey, shapeId)

        // When
        val copy = original.copy()

        // Then
        assertEquals(original, copy)
        assertEquals(original.requestKey, copy.requestKey)
        assertEquals(original.shapeId, copy.shapeId)
    }

    @Test
    fun copy_givenNewRequestKey_whenCopy_thenUpdatesRequestKey() {
        // Given
        val originalRequestKey = createByIdKey(id = "123")
        val newRequestKey = createByIdKey(id = "456")
        val shapeId = createShapeId()
        val original = RootRef(originalRequestKey, shapeId)

        // When
        val copy = original.copy(requestKey = newRequestKey)

        // Then
        assertNotEquals(original, copy)
        assertEquals(newRequestKey, copy.requestKey)
        assertEquals(shapeId, copy.shapeId)
    }

    @Test
    fun copy_givenNewShapeId_whenCopy_thenUpdatesShapeId() {
        // Given
        val requestKey = createByIdKey()
        val originalShapeId = createShapeId(value = "shape1")
        val newShapeId = createShapeId(value = "shape2")
        val original = RootRef(requestKey, originalShapeId)

        // When
        val copy = original.copy(shapeId = newShapeId)

        // Then
        assertNotEquals(original, copy)
        assertEquals(requestKey, copy.requestKey)
        assertEquals(newShapeId, copy.shapeId)
    }

    @Test
    fun copy_givenBothChanged_whenCopy_thenUpdatesBoth() {
        // Given
        val originalRequestKey = createByIdKey(id = "123")
        val newRequestKey = createQueryKey()
        val originalShapeId = createShapeId(value = "shape1")
        val newShapeId = createShapeId(value = "shape2")
        val original = RootRef(originalRequestKey, originalShapeId)

        // When
        val copy = original.copy(requestKey = newRequestKey, shapeId = newShapeId)

        // Then
        assertNotEquals(original, copy)
        assertEquals(newRequestKey, copy.requestKey)
        assertEquals(newShapeId, copy.shapeId)
    }

    // ===== Companion Object parse() Tests =====

    @Test
    fun parse_givenValidFormat_whenParse_thenThrowsNotImplementedError() {
        // Given
        val input = "1234567890:shape1"

        // When/Then
        assertFailsWith<NotImplementedError> {
            RootRef.parse(input)
        }
    }

    @Test
    fun parse_givenInvalidFormatNoColon_whenParse_thenThrowsIllegalArgumentException() {
        // Given
        val input = "invalidformat"

        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            RootRef.parse(input)
        }
        assertEquals("Invalid RootRef format: $input", exception.message)
    }

    @Test
    fun parse_givenEmptyString_whenParse_thenThrowsIllegalArgumentException() {
        // Given
        val input = ""

        // When/Then
        val exception = assertFailsWith<IllegalArgumentException> {
            RootRef.parse(input)
        }
        assertEquals("Invalid RootRef format: $input", exception.message)
    }

    @Test
    fun parse_givenOnlyColon_whenParse_thenThrowsNotImplementedError() {
        // Given
        val input = ":"

        // When/Then
        // This has valid format (splits into ["", ""]) but reaches TODO
        assertFailsWith<NotImplementedError> {
            RootRef.parse(input)
        }
    }

    @Test
    fun parse_givenMultipleColons_whenParse_thenSplitsCorrectlyAndThrowsNotImplementedError() {
        // Given
        val input = "123:shape:extra"

        // When/Then
        // limit=2 means it splits into ["123", "shape:extra"]
        // This is valid format, so it reaches TODO
        assertFailsWith<NotImplementedError> {
            RootRef.parse(input)
        }
    }

    @Test
    fun parse_givenColonAtEnd_whenParse_thenThrowsNotImplementedError() {
        // Given
        val input = "123:"

        // When/Then
        // Valid format: splits into ["123", ""]
        assertFailsWith<NotImplementedError> {
            RootRef.parse(input)
        }
    }

    @Test
    fun parse_givenColonAtStart_whenParse_thenThrowsNotImplementedError() {
        // Given
        val input = ":shape1"

        // When/Then
        // Valid format: splits into ["", "shape1"]
        assertFailsWith<NotImplementedError> {
            RootRef.parse(input)
        }
    }

    // ===== Integration Tests =====

    @Test
    fun integration_givenDifferentStoreKeyTypes_whenUsedInSet_thenWorksCorrectly() {
        // Given
        val byIdKey = createByIdKey(id = "123")
        val queryKey = createQueryKey()
        val shapeId = createShapeId()
        val rootRef1 = RootRef(byIdKey, shapeId)
        val rootRef2 = RootRef(queryKey, shapeId)

        // When
        val set = setOf(rootRef1, rootRef2)

        // Then
        assertEquals(2, set.size)
        assertEquals(true, set.contains(rootRef1))
        assertEquals(true, set.contains(rootRef2))
    }

    @Test
    fun integration_givenDuplicateValues_whenUsedInSet_thenDeduplicates() {
        // Given
        val requestKey = createByIdKey()
        val shapeId = createShapeId()
        val rootRef1 = RootRef(requestKey, shapeId)
        val rootRef2 = RootRef(requestKey, shapeId)

        // When
        val set = setOf(rootRef1, rootRef2)

        // Then
        assertEquals(1, set.size)
    }

    @Test
    fun integration_givenUsedAsMapKey_whenAccessingMap_thenWorksCorrectly() {
        // Given
        val requestKey = createByIdKey()
        val shapeId = createShapeId()
        val rootRef = RootRef(requestKey, shapeId)
        val map = mutableMapOf<RootRef, String>()

        // When
        map[rootRef] = "test-value"
        val retrievedValue = map[rootRef]
        val equivalentRootRef = RootRef(requestKey, shapeId)
        val valueByEquivalentKey = map[equivalentRootRef]

        // Then
        assertEquals("test-value", retrievedValue)
        assertEquals("test-value", valueByEquivalentKey)
    }

    @Test
    fun toString_givenSpecialCharactersInShapeId_whenToString_thenPreservesCharacters() {
        // Given
        val requestKey = createByIdKey()
        val shapeId = ShapeId("shape:with:colons")
        val rootRef = RootRef(requestKey, shapeId)
        val expectedHash = requestKey.stableHash()

        // When
        val result = rootRef.toString()

        // Then
        assertEquals("$expectedHash:shape:with:colons", result)
    }
}
