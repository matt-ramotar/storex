package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface QueryOneOperation<Query : Any, Node : Any, Error : Any> {
    suspend fun queryOne(query: Query): Result<Node?, Error>
}

