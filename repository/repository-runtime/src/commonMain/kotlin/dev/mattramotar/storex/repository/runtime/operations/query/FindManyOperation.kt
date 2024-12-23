package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface FindManyOperation<Key : Any, Node : Any, Error : Any> {
    suspend fun findMany(keys: List<Key>): Result<List<Node>, Error>
}