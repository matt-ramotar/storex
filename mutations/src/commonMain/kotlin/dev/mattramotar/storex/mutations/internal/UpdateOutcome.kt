package dev.mattramotar.storex.mutations.internal

import kotlin.time.Duration

/**
 * User-facing result of an update operation in mutation DSL.
 *
 * Returned by update functions configured in `mutations { update { ... } }`.
 * Similar to [Updater.Outcome] but designed for DSL ergonomics.
 *
 * @param N The network response type
 *
 * ## Example
 * ```kotlin
 * mutationStore<UserKey, User, UserPatch, UserDraft> {
 *     mutations {
 *         update { key, patch ->
 *             try {
 *                 val response = api.patchUser(key.id, patch)
 *                 UpdateOutcome.Success(
 *                     networkEcho = response,
 *                     etag = response.headers["ETag"]
 *                 )
 *             } catch (e: HttpException) {
 *                 when (e.statusCode) {
 *                     412 -> UpdateOutcome.Conflict(serverVersionTag = e.etag)
 *                     429 -> UpdateOutcome.Failure(e, retryAfter = e.retryAfter)
 *                     else -> UpdateOutcome.Failure(e, retryAfter = null)
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
sealed interface UpdateOutcome<out N> {
    /**
     * Update succeeded.
     *
     * @property networkEcho Optional network response to update local cache
     * @property etag Optional ETag for subsequent conditional requests
     */
    data class Success<N>(val networkEcho: N?, val etag: String?) : UpdateOutcome<N>

    /**
     * Update failed due to precondition violation (optimistic concurrency conflict).
     *
     * @property serverVersionTag Current ETag/version from server (if available)
     */
    data class Conflict(val serverVersionTag: String?) : UpdateOutcome<Nothing>

    /**
     * Update failed with an error.
     *
     * @property error The error that occurred
     * @property retryAfter Optional duration to wait before retrying (for rate limiting)
     */
    data class Failure(val error: Throwable, val retryAfter: Duration?) : UpdateOutcome<Nothing>
}
