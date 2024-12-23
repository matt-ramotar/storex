package dev.mattramotar.storex.repository.runtime.operations.observation

import dev.mattramotar.storex.result.Result
import kotlinx.coroutines.flow.Flow

interface ObserveManyOperation<Key : Any, Node : Any, Error : Any> {
    fun observeMany(keys: List<Key>): Flow<Result<List<Node>, Error>>
}