package dev.mattramotar.storex.pager.core


/**
 * Represents more granular results of a pager operation.
 *
 * - [Success]: All requested data loaded successfully.
 * - [Error]: A complete failure occurred with no new data returned.
 */
sealed class PagerResult<out T> {

    /**
     * Complete success - all data requested was successfully loaded.
     */
    data class Success<T>(val items: List<T>) : PagerResult<T>() // TODO: Support PartialSuccess where some data could be loaded, but there were failures for part of the data

    /**
     * A complete error - no new data is loaded.
     *
     * @param error The underlying error detailing what went wrong.
     * @param retry A function you can invoke to attempt the exact same operation again.
     */
    data class Error<T>(
        val error: PagerError,
        val retry: suspend () -> PagerResult<T>
    ) : PagerResult<T>()
}

