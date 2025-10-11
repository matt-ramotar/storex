package dev.mattramotar.storex.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Native-specific platform tests for value classes and memory layout.
 */
class NativePlatformTest {

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
        assertEquals(hash1, hash2, "Same namespaces should have same hash on Native")
        assertNotEquals(hash1, hash3, "Different namespaces should have different hash")
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
        assertEquals(hash1, hash2, "Stable hash should be consistent on Native")
    }

    @Test
    fun queryKey_stableHash_orderIndependent() {
        // Given
        val key1 = QueryKey(
            namespace = StoreNamespace("search"),
            query = mapOf("q" to "kotlin", "limit" to "10")
        )
        val key2 = QueryKey(
            namespace = StoreNamespace("search"),
            query = mapOf("limit" to "10", "q" to "kotlin")
        )

        // When
        val hash1 = key1.stableHash()
        val hash2 = key2.stableHash()

        // Then
        assertEquals(hash1, hash2, "Query keys should be order-independent on Native")
    }

    @Test
    fun valueClasses_asMapKeys_works() {
        // Given
        val map = mutableMapOf<StoreNamespace, String>()
        val namespace = StoreNamespace("users")

        // When
        map[namespace] = "data"

        // Then
        assertEquals("data", map[StoreNamespace("users")])
    }

    @Test
    fun storeKeys_asMapKeys_works() {
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
