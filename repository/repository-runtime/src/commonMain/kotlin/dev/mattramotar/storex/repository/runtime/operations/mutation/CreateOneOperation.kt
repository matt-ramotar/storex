package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.result.Result

interface CreateOneOperation<Key : Any, Properties : Any, Node : Any, Error : Any> {
    suspend fun createOne(key: Key, properties: Properties): Result<Node, Error>
}