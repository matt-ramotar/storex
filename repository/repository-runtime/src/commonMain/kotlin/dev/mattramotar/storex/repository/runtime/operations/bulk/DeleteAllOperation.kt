package dev.mattramotar.storex.repository.runtime.operations.bulk

import dev.mattramotar.storex.result.Result

interface DeleteAllOperation<Error : Any> {
    suspend fun deleteAll(): Result<Int, Error>
}