package dev.mattramotar.storex.mutablestore.core.api

import dev.mattramotar.storex.result.Result

fun interface UpdateOperation<Key : Any, Value : Any, Error : Any> {
    suspend fun update(key: Key, value: Value): Result<Value, Error>
}