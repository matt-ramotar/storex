package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface ReplaceOneOperation<Node, Error> {
    suspend fun replaceOne(node: Node, dataSources: DataSources? = null): Result<Int, Error>
}