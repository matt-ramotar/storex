package dev.mattramotar.storex.mutations

import dev.mattramotar.storex.core.ByIdKey
import dev.mattramotar.storex.core.EntityId
import dev.mattramotar.storex.core.StoreNamespace
import kotlinx.serialization.Serializable

/**
 * Test domain model
 */
@Serializable
data class TestUser(
    val id: String,
    val name: String,
    val email: String,
    val version: Int = 0
)

/**
 * Test patch type
 */
@Serializable
data class TestUserPatch(
    val name: String? = null,
    val email: String? = null
)

/**
 * Test draft type for creation
 */
@Serializable
data class TestUserDraft(
    val name: String,
    val email: String
)

/**
 * Helper to create a test key
 */
fun testUserKey(id: String): ByIdKey = ByIdKey(
    namespace = StoreNamespace("users"),
    entity = EntityId("User", id)
)

/**
 * Helper to create a test user
 */
fun testUser(
    id: String = "user-123",
    name: String = "Test User",
    email: String = "test@example.com",
    version: Int = 0
) = TestUser(id, name, email, version)

/**
 * Helper to apply a patch to a user
 */
fun TestUser.applyPatch(patch: TestUserPatch): TestUser {
    return copy(
        name = patch.name ?: name,
        email = patch.email ?: email,
        version = version + 1
    )
}
