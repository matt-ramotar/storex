package dev.mattramotar.storex.mutablestore.core.api

import dev.mattramotar.storex.result.Result

fun interface CreateOperation<Key : Any, Partial : Any, Value : Any, Error : Any> {
    suspend fun create(key: Key, partial: Partial): Result<Value, Error>
}