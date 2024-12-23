package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface FindAllOperation<Node : Any, Error : Any> {
    suspend fun findAll(): Result<List<Node>, Error>
}