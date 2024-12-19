package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface FindOneCompositeOperation<Key, Composite, Error> {
    suspend fun findOneComposite(key: Key, dataSources: DataSources): Result<Composite, Error>
}