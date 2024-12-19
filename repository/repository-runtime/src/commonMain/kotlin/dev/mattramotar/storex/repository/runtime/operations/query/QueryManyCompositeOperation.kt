package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface QueryManyCompositeOperation<Query, Composite, Error> {
    suspend fun queryManyComposite(query: Query, dataSources: DataSources): Result<List<Composite>, Error>
}