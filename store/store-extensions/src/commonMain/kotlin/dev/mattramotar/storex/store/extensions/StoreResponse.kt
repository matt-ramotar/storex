package dev.mattramotar.storex.store.extensions

sealed class StoreResponse<out D, out E> {
    data object Loading : StoreResponse<Nothing, Nothing>()

    sealed class Result<D, E> : StoreResponse<D, E>() {
        data object NoNewData : Result<Nothing, Nothing>()
        data class Success<D>(val data: D) : Result<D, Nothing>()
        data class Failure<E>(val error: E) : Result<Nothing, E>()
    }
}