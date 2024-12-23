package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface QueryOneCompositeOperation<Query : Any, Composite : Any, Error : Any> {
    suspend fun queryOneComposite(query: Query): Result<Composite?, Error>
}