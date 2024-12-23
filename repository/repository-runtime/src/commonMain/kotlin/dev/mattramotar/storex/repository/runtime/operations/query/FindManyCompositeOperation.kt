package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface FindManyCompositeOperation<Key : Any, Composite : Any, Error : Any> {
    suspend fun findManyComposite(keys: List<Key>): Result<List<Composite>, Error>
}