package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface InsertOneOperation<Properties, Node, Error> {
    suspend fun insertOne(properties: Properties, dataSources: DataSources? = null): Result<Node, Error>
}