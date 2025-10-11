package dev.mattramotar.storex.mutations.internal

import dev.mattramotar.storex.core.ByIdKey
import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.StoreResult
import dev.mattramotar.storex.mutations.*
import dev.mattramotar.storex.mutations.dsl.mutationStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealMutationStoreTest {

    // ========== CREATE TESTS ==========

    @Test
    fun create_givenValidDraft_thenReturnsSuccessWithCanonicalKey() = runTest {
        // Given
        val draft = TestUserDraft(name = "Alice", email = "alice@example.com")
        var createdUser: TestUser? = null

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }

            mutations {
                create { d: TestUserDraft ->
                    val user = testUser(id = "server-123", name = d.name, email = d.email)
                    createdUser = user
                    PostClient.Response.Success(
                        canonicalKey = testUserKey("server-123"),
                        echo = user,
                        etag = "etag-1"
                    )
                }
            }
        }

        // When
        val result = store.create(draft)

        // Then
        assertIs<CreateResult.Synced<ByIdKey>>(result)
        assertEquals(testUserKey("server-123"), result.canonical)
        assertEquals(null, result.rekeyedFrom)
        assertNotNull(createdUser)
        assertEquals("Alice", createdUser?.name)
        assertEquals("alice@example.com", createdUser?.email)
    }

    @Test
    fun create_givenNetworkFailure_thenReturnsFailedResult() = runTest {
        // Given
        val draft = TestUserDraft(name = "Bob", email = "bob@example.com")
        val error = RuntimeException("Network error")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }

            mutations {
                create { _: TestUserDraft ->
                    PostClient.Response.Failure(error)
                }
            }
        }

        // When
        val result = store.create(draft)

        // Then
        assertIs<CreateResult.Failed<*>>(result)
        assertEquals(error, result.cause)
    }

    // ========== UPDATE TESTS ==========

    @Test
    fun update_givenValidPatch_thenReturnsSuccessAndUpdatesSoT() = runTest {
        // Given
        val key = testUserKey("user-123")
        val patch = TestUserPatch(name = "Updated Name")
        val originalUser = testUser(id = "user-123", name = "Original Name")
        var storedUser: TestUser? = originalUser

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            persistence {
                reader { _: ByIdKey -> storedUser }
                writer { _: ByIdKey, user: TestUser -> storedUser = user }
            }

            mutations {
                update { _: ByIdKey, p: TestUserPatch ->
                    val current = storedUser ?: error("Not found")
                    val updated = current.applyPatch(p as TestUserPatch)
                    PatchClient.Response.Success(
                        echo = updated,
                        etag = "etag-2"
                    )
                }
            }
        }

        // When
        val result = store.update(key, patch)

        // Then
        assertIs<UpdateResult.Synced>(result)

        // Verify SoT was updated
        assertEquals("Updated Name", storedUser?.name)
    }

    @Test
    fun update_givenConflict_thenReturnsFailedResult() = runTest {
        // Given
        val key = testUserKey("user-123")
        val patch = TestUserPatch(name = "Conflicting Update")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            mutations {
                update { _: ByIdKey, _: TestUserPatch ->
                    PatchClient.Response.Conflict(serverVersionTag = "etag-999")
                }
            }
        }

        // When
        val result = store.update(key, patch)

        // Then
        assertIs<UpdateResult.Failed>(result)
        assertTrue(result.cause.message?.contains("Conflict") == true)
    }

    @Test
    fun update_givenNoClient_thenReturnsEnqueued() = runTest {
        // Given
        val key = testUserKey("user-123")
        val patch = TestUserPatch(email = "newemail@example.com")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }
            // No mutations configured - defaults to offline-first
        }

        // When
        val result = store.update(key, patch)

        // Then
        assertIs<UpdateResult.Enqueued>(result)
    }

    // ========== DELETE TESTS ==========

    @Test
    fun delete_givenValidKey_thenReturnsSuccessAndRemovesFromSoT() = runTest {
        // Given
        val key = testUserKey("user-123")
        var deleteCalled = false

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            persistence {
                reader { _: ByIdKey -> testUser() }
                writer { _: ByIdKey, _: TestUser -> }
                deleter { _: ByIdKey -> deleteCalled = true }
            }

            mutations {
                delete { _: ByIdKey ->
                    DeleteClient.Response.Success(alreadyDeleted = false, etag = "etag-3")
                }
            }
        }

        // When
        val result = store.delete(key)

        // Then
        assertIs<DeleteResult.Synced>(result)
        assertEquals(false, result.alreadyDeleted)
        assertTrue(deleteCalled)
    }

    @Test
    fun delete_givenAlreadyDeleted_thenReturnsSuccessWithFlag() = runTest {
        // Given
        val key = testUserKey("user-123")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            mutations {
                delete { _: ByIdKey ->
                    DeleteClient.Response.Success(alreadyDeleted = true, etag = null)
                }
            }
        }

        // When
        val result = store.delete(key)

        // Then
        assertIs<DeleteResult.Synced>(result)
        assertEquals(true, result.alreadyDeleted)
    }

    @Test
    fun delete_givenNetworkFailure_thenReturnsFailedResult() = runTest {
        // Given
        val key = testUserKey("user-123")
        val error = RuntimeException("Delete failed")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            mutations {
                delete { _: ByIdKey ->
                    DeleteClient.Response.Failure(error)
                }
            }
        }

        // When
        val result = store.delete(key)

        // Then
        assertIs<DeleteResult.Failed>(result)
        assertEquals(error, result.cause)
        assertEquals(false, result.restored)
    }

    // ========== UPSERT TESTS ==========

    @Test
    fun upsert_givenNewEntity_thenReturnsSuccessWithCreatedFlag() = runTest {
        // Given
        val key = testUserKey("user-456")
        val user = testUser(id = "user-456", name = "New User")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            mutations {
                upsert { _: ByIdKey, value: TestUser ->
                    PutClient.Response.Created(
                        echo = value,
                        etag = "etag-4"
                    )
                }
            }
        }

        // When
        val result = store.upsert(key, user)

        // Then
        assertIs<UpsertResult.Synced<ByIdKey>>(result)
        assertEquals(key, result.key)
        assertEquals(true, result.created)
    }

    @Test
    fun upsert_givenExistingEntity_thenReturnsSuccessWithReplacedFlag() = runTest {
        // Given
        val key = testUserKey("user-456")
        val user = testUser(id = "user-456", name = "Updated User")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            mutations {
                upsert { _: ByIdKey, value: TestUser ->
                    PutClient.Response.Replaced(
                        echo = value,
                        etag = "etag-5"
                    )
                }
            }
        }

        // When
        val result = store.upsert(key, user)

        // Then
        assertIs<UpsertResult.Synced<ByIdKey>>(result)
        assertEquals(key, result.key)
        assertEquals(false, result.created)
    }

    @Test
    fun upsert_givenNoClient_thenReturnsLocalResult() = runTest {
        // Given
        val key = testUserKey("user-789")
        val user = testUser(id = "user-789")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }
            // No mutations configured
        }

        // When
        val result = store.upsert(key, user)

        // Then
        assertIs<UpsertResult.Local<ByIdKey>>(result)
        assertEquals(key, result.key)
    }

    // ========== REPLACE TESTS ==========

    @Test
    fun replace_givenExistingEntity_thenReturnsSuccess() = runTest {
        // Given
        val key = testUserKey("user-123")
        val updatedUser = testUser(id = "user-123", name = "Replaced User", version = 2)

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            mutations {
                replace { _: ByIdKey, value: TestUser ->
                    PutClient.Response.Replaced(
                        echo = value,
                        etag = "etag-6"
                    )
                }
            }
        }

        // When
        val result = store.replace(key, updatedUser)

        // Then
        assertIs<ReplaceResult.Synced>(result)
    }

    @Test
    fun replace_givenNetworkFailure_thenReturnsFailedResult() = runTest {
        // Given
        val key = testUserKey("user-123")
        val user = testUser(id = "user-123")
        val error = RuntimeException("Replace failed")

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { k: ByIdKey -> testUser(id = k.entity.id) }

            mutations {
                replace { _: ByIdKey, _: TestUser ->
                    PutClient.Response.Failure(error)
                }
            }
        }

        // When
        val result = store.replace(key, user)

        // Then
        assertIs<ReplaceResult.Failed>(result)
        assertEquals(error, result.cause)
    }

    // ========== INTEGRATION TESTS ==========

    @Test
    fun store_givenCRUDSequence_thenAllOperationsWorkTogether() = runTest {
        // Given
        val users = mutableMapOf<String, TestUser>()

        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> users[key.entity.id] ?: error("Not found") }

            persistence {
                reader { key: ByIdKey -> users[key.entity.id] }
                writer { key: ByIdKey, user: TestUser -> users[key.entity.id] = user }
                deleter { key: ByIdKey -> users.remove(key.entity.id) }
            }

            mutations {
                create { draft: TestUserDraft ->
                    val id = "user-${users.size + 1}"
                    val user = testUser(id = id, name = draft.name, email = draft.email)
                    users[id] = user
                    PostClient.Response.Success(
                        canonicalKey = testUserKey(id),
                        echo = user,
                        etag = "etag-create"
                    )
                }

                update { key: ByIdKey, patch: TestUserPatch ->
                    val current = users[key.entity.id] ?: error("Not found")
                    val updated = current.applyPatch(patch as TestUserPatch)
                    users[key.entity.id] = updated
                    PatchClient.Response.Success(
                        echo = updated,
                        etag = "etag-update"
                    )
                }

                delete { key: ByIdKey ->
                    users.remove(key.entity.id)
                    DeleteClient.Response.Success(alreadyDeleted = false)
                }
            }
        }

        // When/Then - CREATE
        val createResult = store.create(TestUserDraft("Alice", "alice@example.com"))
        assertIs<CreateResult.Synced<ByIdKey>>(createResult)
        val key = createResult.canonical

        // When/Then - READ
        val readResult = store.stream(key, Freshness.MustBeFresh).first()
        assertIs<StoreResult.Data<*>>(readResult)
        assertEquals("Alice", (readResult.value as TestUser).name)

        // When/Then - UPDATE
        val updateResult = store.update(key, TestUserPatch(name = "Alice Updated"))
        assertIs<UpdateResult.Synced>(updateResult)

        val afterUpdate = store.stream(key, Freshness.MustBeFresh).first()
        assertIs<StoreResult.Data<*>>(afterUpdate)
        assertEquals("Alice Updated", (afterUpdate.value as TestUser).name)

        // When/Then - DELETE
        val deleteResult = store.delete(key)
        assertIs<DeleteResult.Synced>(deleteResult)

        // Verify deleted
        assertTrue(users[key.entity.id] == null)
    }
}
