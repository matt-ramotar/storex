package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.result.Result

interface UpdateOneOperation<Key: Any, Node : Any, Error : Any> {
    suspend fun updateOne(key: Key, node: Node): Result<Node, Error>
}