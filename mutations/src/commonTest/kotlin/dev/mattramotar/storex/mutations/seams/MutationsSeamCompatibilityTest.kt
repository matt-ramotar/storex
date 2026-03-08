package dev.mattramotar.storex.mutations.seams

import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.seams.Fetcher
import dev.mattramotar.storex.core.seams.FetcherResult
import dev.mattramotar.storex.mutations.TestUser
import dev.mattramotar.storex.mutations.TestUserDraft
import dev.mattramotar.storex.mutations.TestUserPatch
import dev.mattramotar.storex.mutations.UpdateResult
import dev.mattramotar.storex.mutations.applyPatch
import dev.mattramotar.storex.mutations.dsl.mutationStore
import dev.mattramotar.storex.mutations.testUser
import dev.mattramotar.storex.mutations.testUserKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MutationsSeamCompatibilityTest {

    @Test
    fun mutationStore_acceptsPublicSeamFetcher_and_preserves_readWriteInvalidationBehavior() = runTest {
        val key = testUserKey("user-123")
        val users = mutableMapOf(key.entity.id to testUser(id = key.entity.id))
        var fetchCount = 0

        val store = mutationStore<dev.mattramotar.storex.core.ByIdKey, TestUser, TestUserPatch, TestUserDraft>(
            scope = this
        ) {
            fetcher = Fetcher { requestedKey, _ ->
                fetchCount++
                flowOf(
                    FetcherResult.Success(
                        users[requestedKey.entity.id] ?: testUser(id = requestedKey.entity.id)
                    )
                )
            }

            persistence {
                reader { requestedKey -> users[requestedKey.entity.id] }
                writer { requestedKey, user -> users[requestedKey.entity.id] = user }
                deleter { requestedKey -> users.remove(requestedKey.entity.id) }
            }

            mutations {
                update { requestedKey, patch ->
                    val updated = requireNotNull(users[requestedKey.entity.id]).applyPatch(patch)
                    users[requestedKey.entity.id] = updated
                    dev.mattramotar.storex.mutations.PatchClient.Response.Success(
                        echo = updated,
                        etag = "etag-updated"
                    )
                }
            }
        }

        val initial = store.get(key, Freshness.MustBeFresh)
        assertEquals("Test User", initial.name)
        assertEquals(1, fetchCount)

        val updateResult = store.update(key, TestUserPatch(name = "Updated"))
        assertIs<UpdateResult.Synced>(updateResult)
        assertEquals("Updated", users[key.entity.id]?.name)

        store.invalidate(key)
        runCurrent()

        val refreshed = store.get(key, Freshness.MustBeFresh)
        assertEquals("Updated", refreshed.name)
        assertEquals(2, fetchCount)

        store.clear(key)
        runCurrent()
        assertNull(users[key.entity.id])
    }
}
