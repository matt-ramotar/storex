package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface FindManyCompositeOperation<Key, Composite, Error> {
    suspend fun findManyComposite(keys: List<Key>, dataSources: DataSources): Result<List<Composite>, Error>
}