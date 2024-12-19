package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface UpsertOneOperation<Properties, Node, Error> {
    suspend fun upsertOne(properties: Properties, dataSources: DataSources? = null): Result<Node, Error>
}