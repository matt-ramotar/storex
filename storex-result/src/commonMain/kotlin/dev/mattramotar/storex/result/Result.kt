package dev.mattramotar.storex.result

sealed class Result<out T, out E> {
    data class Success<T>(val value: T) : Result<T, Nothing>()
    data class Failure<E>(val error: E) : Result<Nothing, E>()
    data class NoOp(val reason: String?): Result<Nothing, Nothing>()

    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (E) -> R, onNoOp: (String?) -> R): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
        is NoOp -> onNoOp(reason)
    }

    fun getOrNull(): T? = when (this) {
        is Success -> value

        is NoOp,
        is Failure -> null
    }

    fun errorOrNull(): E? = when (this) {
        is NoOp,
        is Success -> null

        is Failure -> error
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value

        is NoOp,
        is Failure -> throw IllegalStateException()
    }

    fun errorOrThrow(): E = when (this) {
        is NoOp,
        is Success -> throw IllegalStateException()

        is Failure -> error
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T, E> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (E) -> Unit): Result<T, E> {
        if (this is Failure) action(error)
        return this
    }

    inline fun <U> map(transform: (T) -> U): Result<U, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
        is NoOp -> this
    }

    inline fun <F> mapError(transform: (E) -> F): Result<T, F> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
        is NoOp -> this
    }

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure
}