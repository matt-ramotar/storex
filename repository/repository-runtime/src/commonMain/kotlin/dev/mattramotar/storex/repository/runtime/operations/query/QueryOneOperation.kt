package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface QueryOneOperation<Query, Node, Error> {
    suspend fun queryOne(query: Query, dataSources: DataSources): Result<Node?, Error>
}

