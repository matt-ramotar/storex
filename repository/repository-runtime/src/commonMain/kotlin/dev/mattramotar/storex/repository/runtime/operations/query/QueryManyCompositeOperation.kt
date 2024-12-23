package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface QueryManyCompositeOperation<Query : Any, Composite : Any, Error : Any> {
    suspend fun queryManyComposite(query: Query): Result<List<Composite>, Error>
}