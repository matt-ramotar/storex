package dev.mattramotar.storex.mutations.internal

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.mutations.Precondition

/**
 * Interface for executing update/patch operations against a remote API.
 *
 * Performs partial updates (PATCH semantics) with:
 * - Optimistic concurrency control via preconditions
 * - Network echo support for immediate local updates
 * - Conflict detection and resolution
 *
 * @param Key The [StoreKey] subtype
 * @param Patch The domain patch type
 * @param NetPatch The network DTO for PATCH requests
 *
 * ## Example: HTTP PATCH Implementation
 * ```kotlin
 * class HttpUpdater<Key : StoreKey, UserPatch, UserPatchDto> : Updater<Key, UserPatch, UserPatchDto> {
 *     override suspend fun update(
 *         key: Key,
 *         patch: UserPatch,
 *         body: UserPatchDto,
 *         precondition: Precondition?
 *     ): Outcome<UserDto> {
 *         val headers = mutableMapOf<String, String>()
 *         when (precondition) {
 *             is Precondition.IfMatch -> headers["If-Match"] = precondition.etag
 *             is Precondition.Version -> headers["If-Unmodified-Since-Version"] = precondition.value.toString()
 *             else -> {}
 *         }
 *
 *         return try {
 *             val response = httpClient.patch("/users/${key.id}") {
 *                 headers.forEach { (k, v) -> header(k, v) }
 *                 setBody(body)
 *             }
 *
 *             when (response.status.value) {
 *                 200 -> Outcome.Success(
 *                     echo = response.body(),
 *                     etag = response.headers["ETag"]
 *                 )
 *                 412 -> Outcome.Conflict(
 *                     serverVersionTag = response.headers["ETag"]
 *                 )
 *                 else -> Outcome.Failure(
 *                     HttpException(response.status.value, response.bodyAsText())
 *                 )
 *             }
 *         } catch (e: Exception) {
 *             Outcome.Failure(e)
 *         }
 *     }
 * }
 * ```
 */
interface Updater<K : StoreKey, Patch, NetPatch> {
    /**
     * Result of an update operation.
     *
     * @param Net The network response type
     */
    sealed interface Outcome<out Net> {
        /**
         * Update succeeded.
         *
         * @property echo Optional network response to update local cache
         * @property etag Optional ETag for subsequent conditional requests
         */
        data class Success<Net>(val echo: Net? = null, val etag: String? = null) : Outcome<Net>

        /**
         * Update failed due to precondition violation (HTTP 412 Precondition Failed).
         *
         * Indicates that the provided ETag or version doesn't match the current server state.
         * Client should refetch, merge changes, and retry.
         *
         * @property serverVersionTag Current ETag from server (if available)
         */
        data class Conflict(val serverVersionTag: String? = null) : Outcome<Nothing>

        /**
         * Update failed with an error.
         *
         * @property error The error that occurred
         */
        data class Failure(val error: Throwable) : Outcome<Nothing>
    }

    /**
     * Executes a patch/update operation against the remote API.
     *
     * @param key The entity identifier
     * @param patch The domain patch (for logging/context)
     * @param body The network DTO to send
     * @param precondition Optional precondition (If-Match, If-Unmodified-Since, etc.)
     * @return Outcome indicating success, conflict, or failure
     */
    suspend fun update(key: K, patch: Patch, body: NetPatch, precondition: Precondition?): Outcome<*>
}
