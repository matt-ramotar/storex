package dev.mattramotar.storex.store


/**
 * The response to a [PageLoad.loadPage] request.
 *
 * - [Success]: Returns a list of items successfully loaded.
 * - [Failure]: Includes an error detailing why the load failed.
 *
 * Use this response to determine if the pager should update its state with new items or display an error.
 *
 * @param T The type of items loaded.
 */
sealed class StorePageLoadResponse<out T> {
    data class Success<T>(val items: List<T>) : StorePageLoadResponse<T>()
    data class Failure(val error: Throwable) : StorePageLoadResponse<Nothing>()
}