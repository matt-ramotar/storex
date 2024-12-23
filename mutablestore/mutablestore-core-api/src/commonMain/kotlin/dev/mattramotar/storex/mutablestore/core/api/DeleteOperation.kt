package dev.mattramotar.storex.mutablestore.core.api

import dev.mattramotar.storex.result.Result

fun interface DeleteOperation<Key : Any, Error : Any> {
    suspend fun delete(key: Key): Result<Unit, Error>
}