package dev.mattramotar.storex.mutations.dsl

import dev.mattramotar.storex.core.ByIdKey
import dev.mattramotar.storex.mutations.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class MutationStoreBuilderTest {

    @Test
    fun mutationStore_givenMinimalConfig_thenCreatesStore() = runTest {
        // When
        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }
        }

        // Then
        assertNotNull(store)
    }

    @Test
    fun mutationStore_givenFullConfig_thenCreatesStore() = runTest {
        // When
        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }

            cache {
                maxSize = 100
                ttl = 5.minutes
            }

            persistence {
                reader { key: ByIdKey -> testUser(id = key.entity.id) }
                writer { _: ByIdKey, _: TestUser -> }
            }

            freshness {
                ttl = 5.minutes
            }

            mutations {
                create { draft: TestUserDraft ->
                    PostClient.Response.Success(
                        canonicalKey = testUserKey("new"),
                        echo = testUser(name = draft.name, email = draft.email),
                        etag = null
                    )
                }

                update { _: ByIdKey, _: TestUserPatch ->
                    PatchClient.Response.Success(
                        echo = testUser(),
                        etag = null
                    )
                }

                delete { _: ByIdKey ->
                    DeleteClient.Response.Success(alreadyDeleted = false)
                }

                upsert { _: ByIdKey, value: TestUser ->
                    PutClient.Response.Created(
                        echo = value,
                        etag = null
                    )
                }

                replace { _: ByIdKey, value: TestUser ->
                    PutClient.Response.Replaced(
                        echo = value,
                        etag = null
                    )
                }
            }
        }

        // Then
        assertNotNull(store)
    }

    @Test
    fun mutationStore_givenCreateMutation_thenCanCreate() = runTest {
        // Given
        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }

            mutations {
                create { draft: TestUserDraft ->
                    PostClient.Response.Success(
                        canonicalKey = testUserKey("created-123"),
                        echo = testUser(id = "created-123", name = draft.name, email = draft.email),
                        etag = "etag-1"
                    )
                }
            }
        }

        // When
        val result = store.create(TestUserDraft("Alice", "alice@test.com"))

        // Then
        assertIs<CreateResult.Synced<ByIdKey>>(result)
    }

    @Test
    fun mutationStore_givenUpdateMutation_thenCanUpdate() = runTest {
        // Given
        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }

            mutations {
                update { _: ByIdKey, patch: TestUserPatch ->
                    PatchClient.Response.Success(
                        echo = testUser(name = patch.name ?: "default"),
                        etag = "etag-2"
                    )
                }
            }
        }

        // When
        val result = store.update(testUserKey("123"), TestUserPatch(name = "Updated"))

        // Then
        assertIs<UpdateResult.Synced>(result)
    }

    @Test
    fun mutationStore_givenDeleteMutation_thenCanDelete() = runTest {
        // Given
        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }

            mutations {
                delete { _: ByIdKey ->
                    DeleteClient.Response.Success(alreadyDeleted = false, etag = "etag-3")
                }
            }
        }

        // When
        val result = store.delete(testUserKey("123"))

        // Then
        assertIs<DeleteResult.Synced>(result)
    }

    @Test
    fun mutationStore_givenUpsertMutation_thenCanUpsert() = runTest {
        // Given
        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }

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
        val result = store.upsert(testUserKey("123"), testUser(id = "123"))

        // Then
        assertIs<UpsertResult.Synced<ByIdKey>>(result)
    }

    @Test
    fun mutationStore_givenReplaceMutation_thenCanReplace() = runTest {
        // Given
        val store = mutationStore<ByIdKey, TestUser, TestUserPatch, TestUserDraft> {
            fetcher { key: ByIdKey -> testUser(id = key.entity.id) }

            mutations {
                replace { _: ByIdKey, value: TestUser ->
                    PutClient.Response.Replaced(
                        echo = value,
                        etag = "etag-5"
                    )
                }
            }
        }

        // When
        val result = store.replace(testUserKey("123"), testUser(id = "123"))

        // Then
        assertIs<ReplaceResult.Synced>(result)
    }
}
