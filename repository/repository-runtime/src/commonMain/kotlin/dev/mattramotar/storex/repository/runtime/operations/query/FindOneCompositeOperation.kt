package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.result.Result

interface FindOneCompositeOperation<Key : Any, Composite : Any, Error : Any> {
    suspend fun findOneComposite(key: Key): Result<Composite, Error>
}