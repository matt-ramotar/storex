package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface QueryManyOperation<Query : Any, Node : Any, Error : Any> {
    suspend fun queryMany(query: Query): Result<List<Node>, Error>
}