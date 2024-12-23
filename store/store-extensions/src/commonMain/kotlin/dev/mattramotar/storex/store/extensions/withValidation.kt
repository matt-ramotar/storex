package dev.mattramotar.storex.store.extensions

import dev.mattramotar.storex.store.core.api.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform


fun <Key : Any, Value : Any> Store<Key, Value>.withValidation(
    validator: suspend (Value) -> Boolean,
    onInvalid: suspend (Key) -> Unit = { invalidate(it) }
): Store<Key, Value> {

    val delegate = this

    return object : Store<Key, Value> {
        override fun stream(key: Key): Flow<Value> = delegate.stream(key).transform { data ->
            if (validator(data)) {
                emit(data)
            } else {
                // Data is invalid. Let's trigger a refresh.
                onInvalid(key)

                // After invalidation, future emissions from the original store may yield fresh data.
            }
        }

        override suspend fun get(key: Key): Value? {
            val data = delegate.get(key)

            return if (data != null && validator(data)) data else {
                onInvalid(key)
                delegate.get(key) // Get fresh data after invalidation.
            }
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
