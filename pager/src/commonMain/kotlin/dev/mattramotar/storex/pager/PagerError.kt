package dev.mattramotar.storex.pager

/**
 * A sealed class representing different types of errors that can occur during pagination.
 */
sealed class PagerError : Throwable() {
    data class NetworkError(val causeException: Throwable) : PagerError()
    data class CacheError(val causeException: Throwable) : PagerError()
    data class ParsingError(val causeException: Throwable) : PagerError()
    data class UnknownError(val causeException: Throwable) : PagerError()


    override val cause: Throwable?
        get() {
            return when (this) {
                is NetworkError -> causeException
                is CacheError -> causeException
                is ParsingError -> causeException
                is UnknownError -> causeException
            }
        }
}