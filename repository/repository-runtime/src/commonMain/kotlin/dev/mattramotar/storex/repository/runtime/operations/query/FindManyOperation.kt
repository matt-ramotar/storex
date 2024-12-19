package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface FindManyOperation<Key, Node, Error> {
    suspend fun findMany(keys: List<Key>, dataSources: DataSources): Result<List<Node>, Error>
}