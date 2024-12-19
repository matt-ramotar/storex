package dev.mattramotar.storex.repository.runtime.operations.bulk

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface InsertManyOperation<Properties, Node, Error> {
    suspend fun insertMany(
        properties: List<Properties>,
        dataSources: DataSources? = null
    ): Result<List<Node>, Error>
}