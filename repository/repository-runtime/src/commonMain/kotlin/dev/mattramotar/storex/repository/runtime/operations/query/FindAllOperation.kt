package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface FindAllOperation<Node, Error> {
    suspend fun findAll(dataSources: DataSources): Result<List<Node>, Error>
}