package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface QueryManyOperation<Query, Node, Error> {
    suspend fun queryMany(query: Query, dataSources: DataSources): Result<List<Node>, Error>
}