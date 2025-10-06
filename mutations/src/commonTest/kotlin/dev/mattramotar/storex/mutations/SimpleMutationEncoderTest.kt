package dev.mattramotar.storex.mutations

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimpleMutationEncoderTest {

    @Test
    fun identityEncoder_givenPatch_thenReturnsSameValue() = runTest {
        // Given
        val encoder = IdentityMutationEncoder<TestUser>()
        val patch = testUser(id = "123", name = "Patch")

        // When
        val result = encoder.encodePatch(patch, null)

        // Then
        assertEquals(patch, result)
    }

    @Test
    fun identityEncoder_givenDraft_thenReturnsSameValue() = runTest {
        // Given
        val encoder = IdentityMutationEncoder<TestUser>()
        val draft = testUser(id = "456", name = "Draft")

        // When
        val result = encoder.encodeDraft(draft)

        // Then
        assertEquals(draft, result)
    }

    @Test
    fun identityEncoder_givenValue_thenReturnsSameValue() = runTest {
        // Given
        val encoder = IdentityMutationEncoder<TestUser>()
        val value = testUser(id = "789", name = "Value")

        // When
        val result = encoder.encodeValue(value)

        // Then
        assertEquals(value, result)
    }

    @Test
    fun identityEncoderFactory_thenCreatesIdentityEncoder() = runTest {
        // Given
        val encoder = identityMutationEncoder<TestUser>()
        val user = testUser()

        // When
        val patchResult = encoder.encodePatch(user, null)
        val draftResult = encoder.encodeDraft(user)
        val valueResult = encoder.encodeValue(user)

        // Then
        assertEquals(user, patchResult)
        assertEquals(user, draftResult)
        assertEquals(user, valueResult)
    }

    @Test
    fun customEncoder_givenPatch_thenConvertsCorrectly() = runTest {
        // Given
        val encoder = object : SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, String> {
            override suspend fun encodePatch(patch: TestUserPatch, base: TestUser?): String? {
                return "PATCH:${patch.name}:${patch.email}"
            }

            override suspend fun encodeDraft(draft: TestUserDraft): String? {
                return "DRAFT:${draft.name}:${draft.email}"
            }

            override suspend fun encodeValue(value: TestUser): String {
                return "VALUE:${value.id}:${value.name}:${value.email}"
            }
        }

        val patch = TestUserPatch(name = "Alice", email = "alice@test.com")
        val draft = TestUserDraft(name = "Bob", email = "bob@test.com")
        val value = testUser(id = "123", name = "Charlie", email = "charlie@test.com")

        // When
        val patchResult = encoder.encodePatch(patch, null)
        val draftResult = encoder.encodeDraft(draft)
        val valueResult = encoder.encodeValue(value)

        // Then
        assertEquals("PATCH:Alice:alice@test.com", patchResult)
        assertEquals("DRAFT:Bob:bob@test.com", draftResult)
        assertEquals("VALUE:123:Charlie:charlie@test.com", valueResult)
    }

    @Test
    fun customEncoder_givenApplyPatchLocally_thenAppliesOptimistically() = runTest {
        // Given
        val encoder = object : SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, TestUser> {
            override suspend fun encodePatch(patch: TestUserPatch, base: TestUser?): TestUser? {
                return base?.applyPatch(patch)
            }

            override suspend fun encodeDraft(draft: TestUserDraft): TestUser? {
                return testUser(id = "new", name = draft.name, email = draft.email)
            }

            override suspend fun encodeValue(value: TestUser): TestUser {
                return value
            }

            override suspend fun applyPatchLocally(base: TestUser, patch: TestUserPatch): TestUser {
                return base.applyPatch(patch)
            }
        }

        val base = testUser(id = "123", name = "Original", email = "orig@test.com")
        val patch = TestUserPatch(name = "Updated")

        // When
        val result = encoder.applyPatchLocally(base, patch)

        // Then
        assertEquals("Updated", result.name)
        assertEquals("orig@test.com", result.email)
        assertEquals(1, result.version) // Version incremented by applyPatch
    }

    @Test
    fun customEncoder_givenNullReturn_thenReturnsNull() = runTest {
        // Given
        val encoder = object : SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, String> {
            override suspend fun encodePatch(patch: TestUserPatch, base: TestUser?): String? = null
            override suspend fun encodeDraft(draft: TestUserDraft): String? = null
            override suspend fun encodeValue(value: TestUser): String = "value"
        }

        // When
        val patchResult = encoder.encodePatch(TestUserPatch(name = "Test"), null)
        val draftResult = encoder.encodeDraft(TestUserDraft("Test", "test@test.com"))

        // Then
        assertNull(patchResult)
        assertNull(draftResult)
    }
}
