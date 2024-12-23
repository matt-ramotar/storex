package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.result.Result

interface UpsertOneOperation<Key : Any, Partial : Any, Node : Any, Error : Any> {
    suspend fun upsertOne(key: Key, partial: Partial): Result<Node, Error>
}