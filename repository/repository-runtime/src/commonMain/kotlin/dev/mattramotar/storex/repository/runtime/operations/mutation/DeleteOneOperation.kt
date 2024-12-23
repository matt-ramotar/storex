package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.result.Result

interface DeleteOneOperation<Key : Any, Error : Any> {
    suspend fun deleteOne(key: Key): Result<Int, Error>
}