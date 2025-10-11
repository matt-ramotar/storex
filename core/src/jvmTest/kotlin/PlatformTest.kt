package dev.mattramotar.storex.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * JVM-specific platform tests for value classes and serialization.
 */
class JvmPlatformTest {

    @Test
    fun storeNamespace_hashCode_isConsistent() {
        // Given
        val namespace1 = StoreNamespace("users")
        val namespace2 = StoreNamespace("users")
        val namespace3 = StoreNamespace("articles")

        // When
        val hash1 = namespace1.hashCode()
        val hash2 = namespace2.hashCode()
        val hash3 = namespace3.hashCode()

        // Then
        assertEquals(hash1, hash2, "Same namespaces should have same hash")
        assertNotEquals(hash1, hash3, "Different namespaces should have different hash")
    }

    @Test
    fun storeNamespace_equality_works() {
        // Given
        val namespace1 = StoreNamespace("users")
        val namespace2 = StoreNamespace("users")
        val namespace3 = StoreNamespace("articles")

        // Then
        assertEquals(namespace1, namespace2)
        assertNotEquals(namespace1, namespace3)
    }

    @Test
    fun entityId_hashCode_isConsistent() {
        // Given
        val id1 = EntityId("User", "123")
        val id2 = EntityId("User", "123")
        val id3 = EntityId("User", "456")

        // When
        val hash1 = id1.hashCode()
        val hash2 = id2.hashCode()
        val hash3 = id3.hashCode()

        // Then
        assertEquals(hash1, hash2, "Same IDs should have same hash")
        assertNotEquals(hash1, hash3, "Different IDs should have different hash")
    }

    @Test
    fun byIdKey_stableHash_isConsistent() {
        // Given
        val key1 = ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", "123")
        )
        val key2 = ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", "123")
        )

        // When
        val hash1 = key1.stableHash()
        val hash2 = key2.stableHash()

        // Then
        assertEquals(hash1, hash2, "Same keys should have same stable hash")
    }

    @Test
    fun byIdKey_stableHash_isDifferentForDifferentKeys() {
        // Given
        val key1 = ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", "123")
        )
        val key2 = ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", "456")
        )

        // When
        val hash1 = key1.stableHash()
        val hash2 = key2.stableHash()

        // Then
        assertNotEquals(hash1, hash2, "Different keys should have different stable hash")
    }

    @Test
    fun queryKey_stableHash_isConsistent() {
        // Given
        val key1 = QueryKey(
            namespace = StoreNamespace("search"),
            query = mapOf("q" to "kotlin", "limit" to "10")
        )
        val key2 = QueryKey(
            namespace = StoreNamespace("search"),
            query = mapOf("limit" to "10", "q" to "kotlin") // Different order
        )

        // When
        val hash1 = key1.stableHash()
        val hash2 = key2.stableHash()

        // Then
        assertEquals(hash1, hash2, "Query keys with same params (different order) should have same hash")
    }


    @Test
    fun valueClasses_canBeUsedAsMapKeys() {
        // Given
        val map = mutableMapOf<StoreNamespace, String>()
        val namespace = StoreNamespace("users")

        // When
        map[namespace] = "data"

        // Then
        assertEquals("data", map[StoreNamespace("users")])
    }

    @Test
    fun storeKeys_canBeUsedAsMapKeys() {
        // Given
        val map = mutableMapOf<StoreKey, String>()
        val key = ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", "123")
        )

        // When
        map[key] = "data"

        // Then
        assertEquals("data", map[key])
    }
}
