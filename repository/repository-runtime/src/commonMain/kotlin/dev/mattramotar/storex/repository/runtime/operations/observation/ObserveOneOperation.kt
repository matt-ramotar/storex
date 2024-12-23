package dev.mattramotar.storex.repository.runtime.operations.observation

import dev.mattramotar.storex.result.Result
import kotlinx.coroutines.flow.Flow

interface ObserveOneOperation<Key : Any, Node : Any, Error : Any> {
    fun observeOne(key: Key): Flow<Result<Node?, Error>>
}