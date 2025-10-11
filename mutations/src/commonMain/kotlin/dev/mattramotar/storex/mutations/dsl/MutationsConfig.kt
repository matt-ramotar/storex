package dev.mattramotar.storex.mutations.dsl

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.mutations.DeleteClient
import dev.mattramotar.storex.mutations.PatchClient
import dev.mattramotar.storex.mutations.PostClient
import dev.mattramotar.storex.mutations.PutClient

/**
 * Configuration for mutation operations (update, create, delete, upsert, replace).
 *
 * @param K The store key type
 * @param V The domain value type
 * @param P The patch/update type
 * @param D The draft/create type
 */
class MutationsConfig<K : StoreKey, V : Any, P, D> {
    /**
     * Function to perform partial updates (PATCH).
     * Receives the key and patch, returns a PatchClient.Response.
     */
    var patch: (suspend (K, P) -> PatchClient.Response<*>)? = null

    /**
     * Function to create new entities (POST).
     * Receives a draft, returns a PostClient.Response with the canonical key.
     */
    var post: (suspend (D) -> PostClient.Response<K, *>)? = null

    /**
     * Function to delete entities (DELETE).
     * Receives the key, returns a DeleteClient.Response.
     */
    var delete: (suspend (K) -> DeleteClient.Response)? = null

    /**
     * Function to create-or-replace entities (PUT with upsert semantics).
     * Receives the key and full value, returns a PutClient.Response indicating whether created or replaced.
     */
    var put: (suspend (K, V) -> PutClient.Response<*>)? = null

    /**
     * Function to replace existing entities (PUT with replace-only semantics).
     * Receives the key and full value, returns a PutClient.Response.
     * Typically fails if the entity doesn't exist.
     */
    var replace: (suspend (K, V) -> PutClient.Response<*>)? = null

    /**
     * Configure the update function for partial updates (PATCH).
     *
     * Example:
     * ```kotlin
     * update { key, patch ->
     *     val response = api.updateUser(key.id, patch)
     *     PatchClient.Response.Success(response, response.etag)
     * }
     * ```
     */
    fun update(update: suspend (K, P) -> PatchClient.Response<*>) {
        patch = update
    }

    /**
     * Configure the create function for new entities (POST).
     *
     * Example:
     * ```kotlin
     * create { draft ->
     *     val response = api.createUser(draft)
     *     PostClient.Response.Success(
     *         canonicalKey = UserKey(response.id),
     *         echo = response,
     *         etag = response.etag
     *     )
     * }
     * ```
     */
    fun create(create: suspend (D) -> PostClient.Response<K, *>) {
        post = create
    }

    /**
     * Configure the delete function (DELETE).
     *
     * Example:
     * ```kotlin
     * delete { key ->
     *     api.deleteUser(key.id)
     *     DeleteClient.Response.Success(alreadyDeleted = false)
     * }
     * ```
     */
    fun delete(delete: suspend (K) -> DeleteClient.Response) {
        this@MutationsConfig.delete = delete
    }

    /**
     * Configure the upsert function (PUT with create-or-replace semantics).
     *
     * Example:
     * ```kotlin
     * upsert { key, value ->
     *     val response = api.upsertUser(key.id, value)
     *     if (response.created) {
     *         PutClient.Response.Created(response, response.etag)
     *     } else {
     *         PutClient.Response.Replaced(response, response.etag)
     *     }
     * }
     * ```
     */
    fun upsert(upsert: suspend (K, V) -> PutClient.Response<*>) {
        put = upsert
    }

    /**
     * Configure the replace function (PUT with replace-only semantics).
     *
     * Example:
     * ```kotlin
     * replace { key, value ->
     *     val response = api.replaceUser(key.id, value)
     *     PutClient.Response.Replaced(response, response.etag)
     * }
     * ```
     */
    fun replace(replace: suspend (K, V) -> PutClient.Response<*>) {
        this@MutationsConfig.replace = replace
    }
}
