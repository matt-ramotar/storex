package dev.mattramotar.storex.repository.runtime.operations.bulk

import dev.mattramotar.storex.result.Result

interface DeleteManyOperation<Key : Any, Error : Any> {
    suspend fun deleteMany(keys: List<Key>): Result<Int, Error>
}