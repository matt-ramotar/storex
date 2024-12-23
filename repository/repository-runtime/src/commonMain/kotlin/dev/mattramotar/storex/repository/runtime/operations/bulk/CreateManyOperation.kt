package dev.mattramotar.storex.repository.runtime.operations.bulk

import dev.mattramotar.storex.result.Result

interface CreateManyOperation<Properties : Any, Node : Any, Error : Any> {
    suspend fun insertMany(
        properties: List<Properties>,
    ): Result<List<Node>, Error>
}