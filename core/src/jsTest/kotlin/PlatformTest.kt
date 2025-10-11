package dev.mattramotar.storex.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * JS-specific platform tests for value classes and JSON interop.
 */
class JsPlatformTest {

    @Test
    fun storeNamespace_hashCode_isConsistent() {
        // Given
        val namespace1 = StoreNamespace("users")
        val namespace2 = StoreNamespace("users")

        // When
        val hash1 = namespace1.hashCode()
        val hash2 = namespace2.hashCode()

        // Then
        assertEquals(hash1, hash2, "Same namespaces should have same hash on JS")
    }


    @Test
    fun byIdKey_stableHash_works() {
        // Given
        val key1 = ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", "123")
        )
        val key2 = ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", "123")
        )
        val key3 = ByIdKey(
            namespace = StoreNamespace("users"),
            entity = EntityId("User", "456")
        )

        // When
        val hash1 = key1.stableHash()
        val hash2 = key2.stableHash()
        val hash3 = key3.stableHash()

        // Then
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
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
        assertEquals(hash1, hash2, "Query param order shouldn't affect hash on JS")
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
}
