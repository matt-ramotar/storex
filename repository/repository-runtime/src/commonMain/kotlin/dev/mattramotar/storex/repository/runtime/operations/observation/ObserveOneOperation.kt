package dev.mattramotar.storex.repository.runtime.operations.observation

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result
import kotlinx.coroutines.flow.Flow

interface ObserveOneOperation<Key, Node, Error> {
    fun observeOne(key: Key, dataSources: DataSources): Flow<Result<Node?, Error>>
}