package dev.mattramotar.storex.store.extensions

import dev.mattramotar.storex.store.core.api.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

fun <Key : Any, Value : Any> Store<Key, Value>.withResponseStates(): Store<Key, StoreResponse<Value, Throwable>> {

    val delegate = this.withResponseStates { it }

    return object : Store<Key, StoreResponse<Value, Throwable>> {
        override fun stream(key: Key): Flow<StoreResponse<Value, Throwable>> = delegate.stream(key)
        override suspend fun get(key: Key): StoreResponse<Value, Throwable>? = delegate.get(key)

        override suspend fun clear(key: Key) {
            delegate.clear(key)
        }

        override suspend fun clearAll() {
            delegate.clearAll()
        }

        override suspend fun invalidate(key: Key) {
            delegate.invalidate(key)
        }

    }
}

fun <Key : Any, Value : Any, Error : Any> Store<Key, Value>.withResponseStates(
    errorAdapter: (Throwable) -> Error
): Store<Key, StoreResponse<Value, Error>> {

    val delegate = this

    return object : Store<Key, StoreResponse<Value, Error>> {
        override fun stream(key: Key): Flow<StoreResponse<Value, Error>> = flow {
            emit(StoreResponse.Loading)

            emit(get(key))

            // Listen for future updates as well.
            emitAll(
                delegate
                    .stream(key)
                    .map<Value, StoreResponse<Value, Error>> { StoreResponse.Result.Success(it) }
                    .catch { errorAdapter(it) }
            )
        }

        override suspend fun get(key: Key): StoreResponse<Value, Error> =
            try {
                val data = delegate.get(key)
                if (data == null) {
                    StoreResponse.Result.NoNewData
                } else {
                    StoreResponse.Result.Success(data)
                }
            } catch (error: Throwable) {
                StoreResponse.Result.Failure(errorAdapter(error))
            }

        override suspend fun clear(key: Key) {
            delegate.clear(key)
        }

        override suspend fun clearAll() {
            delegate.clearAll()
        }

        override suspend fun invalidate(key: Key) {
            delegate.invalidate(key)
        }

    }
}
