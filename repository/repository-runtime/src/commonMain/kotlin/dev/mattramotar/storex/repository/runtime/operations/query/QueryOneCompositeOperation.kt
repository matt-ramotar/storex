package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface QueryOneCompositeOperation<Query, Composite, Error> {
    suspend fun queryOneComposite(query: Query, dataSources: DataSources): Result<Composite?, Error>
}