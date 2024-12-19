package dev.mattramotar.storex.repository.runtime.operations.query

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface FindOneOperation<Key, Node, Error> {
    suspend fun findOne(key: Key, dataSources: DataSources): Result<Node, Error>
}