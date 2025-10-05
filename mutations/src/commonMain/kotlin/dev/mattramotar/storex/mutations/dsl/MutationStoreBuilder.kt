package dev.mattramotar.storex.mutations.dsl

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.mutations.MutationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Creates a mutation store using a type-safe DSL.
 *
 * Example:
 * ```kotlin
 * val userStore = mutationStore<UserKey, User, UserPatch, UserDraft> {
 *     fetcher { key -> api.getUser(key.id) }
 *
 *     cache {
 *         maxSize = 100
 *         ttl = 5.minutes
 *     }
 *
 *     persistence {
 *         reader { key -> database.getUser(key.id) }
 *         writer { key, user -> database.saveUser(user) }
 *     }
 *
 *     mutations {
 *         update { key, patch ->
 *             val response = api.updateUser(key.id, patch)
 *             PatchClient.Response.Success(response, response.etag)
 *         }
 *
 *         create { draft ->
 *             val response = api.createUser(draft)
 *             PostClient.Response.Success(UserKey(response.id), response, response.etag)
 *         }
 *
 *         delete { key ->
 *             api.deleteUser(key.id)
 *             DeleteClient.Response.Success(alreadyDeleted = false)
 *         }
 *     }
 * }
 * ```
 *
 * @param K The store key type
 * @param V The domain value type
 * @param P The patch/update type
 * @param D The draft/create type
 * @param scope Optional coroutine scope for the store. Defaults to a new scope with SupervisorJob.
 * @param block Configuration block
 * @return A configured MutationStore instance
 */
fun <K : StoreKey, V : Any, P, D> mutationStore(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    block: MutationStoreBuilderScope<K, V, P, D>.() -> Unit
): MutationStore<K, V, P, D> {
    // TODO: Implement builder
    throw NotImplementedError("MutationStore builder not yet implemented")
}
