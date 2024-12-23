package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.result.Result

interface ReplaceOneOperation<Key: Any, Node : Any, Error : Any> {
    suspend fun replaceOne(key: Key, node: Node): Result<Node, Error>
}