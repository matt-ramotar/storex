package dev.mattramotar.storex.store.dsl

import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.internal.UpdateOutcome
import dev.mattramotar.storex.store.mutation.CreateOutcome
import dev.mattramotar.storex.store.mutation.DeleteOutcome
import dev.mattramotar.storex.store.mutation.PutOutcome

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
     * Receives the key and patch, returns an UpdateOutcome.
     */
    var updater: (suspend (K, P) -> UpdateOutcome<*>)? = null

    /**
     * Function to create new entities (POST).
     * Receives a draft, returns a CreateOutcome with the canonical key.
     */
    var creator: (suspend (D) -> CreateOutcome<K, *>)? = null

    /**
     * Function to delete entities (DELETE).
     * Receives the key, returns a DeleteOutcome.
     */
    var deleter: (suspend (K) -> DeleteOutcome)? = null

    /**
     * Function to create-or-replace entities (PUT with upsert semantics).
     * Receives the key and full value, returns a PutOutcome indicating whether created or replaced.
     */
    var putser: (suspend (K, V) -> PutOutcome<K, *>)? = null

    /**
     * Function to replace existing entities (PUT with replace-only semantics).
     * Receives the key and full value, returns a PutOutcome.
     * Typically fails if the entity doesn't exist.
     */
    var replacer: (suspend (K, V) -> PutOutcome<K, *>)? = null

    /**
     * Configure the update function for partial updates (PATCH).
     *
     * Example:
     * ```kotlin
     * update { key, patch ->
     *     val response = api.updateUser(key.id, patch)
     *     UpdateOutcome.Success(response, response.etag)
     * }
     * ```
     */
    fun update(update: suspend (K, P) -> UpdateOutcome<*>) {
        updater = update
    }

    /**
     * Configure the create function for new entities (POST).
     *
     * Example:
     * ```kotlin
     * create { draft ->
     *     val response = api.createUser(draft)
     *     CreateOutcome.Success(
     *         canonicalKey = UserKey(response.id),
     *         networkEcho = response,
     *         etag = response.etag
     *     )
     * }
     * ```
     */
    fun create(create: suspend (D) -> CreateOutcome<K, *>) {
        creator = create
    }

    /**
     * Configure the delete function (DELETE).
     *
     * Example:
     * ```kotlin
     * delete { key ->
     *     api.deleteUser(key.id)
     *     DeleteOutcome.Success(alreadyDeleted = false)
     * }
     * ```
     */
    fun delete(delete: suspend (K) -> DeleteOutcome) {
        deleter = delete
    }

    /**
     * Configure the upsert function (PUT with create-or-replace semantics).
     *
     * Example:
     * ```kotlin
     * upsert { key, value ->
     *     val response = api.upsertUser(key.id, value)
     *     if (response.created) {
     *         PutOutcome.Created(key, response, response.etag)
     *     } else {
     *         PutOutcome.Replaced(key, response, response.etag)
     *     }
     * }
     * ```
     */
    fun upsert(upsert: suspend (K, V) -> PutOutcome<K, *>) {
        putser = upsert
    }

    /**
     * Configure the replace function (PUT with replace-only semantics).
     *
     * Example:
     * ```kotlin
     * replace { key, value ->
     *     val response = api.replaceUser(key.id, value)
     *     PutOutcome.Replaced(key, response, response.etag)
     * }
     * ```
     */
    fun replace(replace: suspend (K, V) -> PutOutcome<K, *>) {
        replacer = replace
    }
}
