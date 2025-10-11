package dev.mattramotar.storex.mutations

import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [SimpleMutationEncoderAdapter].
 *
 * Verifies that the adapter correctly delegates to the wrapped [SimpleMutationEncoder]
 * and properly adapts its 4-parameter signature to [MutationEncoder]'s 6-parameter signature.
 */
class SimpleMutationEncoderAdapterTest {

    @Test
    fun fromPatch_givenNullBase_whenEncodePatchReturnsValue_thenReturnsValue() = runTest {
        // Given
        val mockEncoder = mock<SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, String>>()
        val patch = TestUserPatch(name = "Alice")
        val expectedNetwork = "PATCH:Alice"
        everySuspend { mockEncoder.encodePatch(patch, null) } returns expectedNetwork

        val adapter = SimpleMutationEncoderAdapter(mockEncoder)

        // When
        val result = adapter.fromPatch(patch, null)

        // Then
        assertEquals(expectedNetwork, result)
    }

    @Test
    fun fromPatch_givenNonNullBase_whenEncodePatchReturnsValue_thenReturnsValue() = runTest {
        // Given
        val mockEncoder = mock<SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, String>>()
        val patch = TestUserPatch(name = "Bob", email = "bob@test.com")
        val base = testUser(id = "123", name = "Original")
        val expectedNetwork = "PATCH:Bob:bob@test.com"
        everySuspend { mockEncoder.encodePatch(patch, base) } returns expectedNetwork

        val adapter = SimpleMutationEncoderAdapter(mockEncoder)

        // When
        val result = adapter.fromPatch(patch, base)

        // Then
        assertEquals(expectedNetwork, result)
    }

    @Test
    fun fromPatch_givenPatch_whenEncodePatchReturnsNull_thenReturnsNull() = runTest {
        // Given
        val mockEncoder = mock<SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, String>>()
        val patch = TestUserPatch(name = "Invalid")
        val base = testUser(id = "456")
        everySuspend { mockEncoder.encodePatch(patch, base) } returns null

        val adapter = SimpleMutationEncoderAdapter(mockEncoder)

        // When
        val result = adapter.fromPatch(patch, base)

        // Then
        assertNull(result)
    }

    @Test
    fun fromDraft_givenDraft_whenEncodeDraftReturnsValue_thenReturnsValue() = runTest {
        // Given
        val mockEncoder = mock<SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, String>>()
        val draft = TestUserDraft(name = "Charlie", email = "charlie@test.com")
        val expectedNetwork = "DRAFT:Charlie:charlie@test.com"
        everySuspend { mockEncoder.encodeDraft(draft) } returns expectedNetwork

        val adapter = SimpleMutationEncoderAdapter(mockEncoder)

        // When
        val result = adapter.fromDraft(draft)

        // Then
        assertEquals(expectedNetwork, result)
    }

    @Test
    fun fromDraft_givenDraft_whenEncodeDraftReturnsNull_thenReturnsNull() = runTest {
        // Given
        val mockEncoder = mock<SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, String>>()
        val draft = TestUserDraft(name = "Invalid", email = "invalid@test.com")
        everySuspend { mockEncoder.encodeDraft(draft) } returns null

        val adapter = SimpleMutationEncoderAdapter(mockEncoder)

        // When
        val result = adapter.fromDraft(draft)

        // Then
        assertNull(result)
    }

    @Test
    fun fromValue_givenValue_whenEncodeValueReturnsValue_thenReturnsValue() = runTest {
        // Given
        val mockEncoder = mock<SimpleMutationEncoder<TestUserPatch, TestUserDraft, TestUser, String>>()
        val value = testUser(id = "789", name = "David", email = "david@test.com")
        val expectedNetwork = "VALUE:789:David:david@test.com"
        everySuspend { mockEncoder.encodeValue(value) } returns expectedNetwork

        val adapter = SimpleMutationEncoderAdapter(mockEncoder)

        // When
        val result = adapter.fromValue(value)

        // Then
        assertEquals(expectedNetwork, result)
    }
}
