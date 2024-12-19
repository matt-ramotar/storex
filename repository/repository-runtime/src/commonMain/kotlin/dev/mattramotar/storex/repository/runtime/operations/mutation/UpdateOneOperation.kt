package dev.mattramotar.storex.repository.runtime.operations.mutation

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface UpdateOneOperation<Node, Error> {
    suspend fun updateOne(node: Node, dataSources: DataSources? = null): Result<Int, Error>
}