package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface FindOneOperation<Key : Any, Node : Any, Error : Any> {
    suspend fun findOne(key: Key): Result<Node, Error>
}