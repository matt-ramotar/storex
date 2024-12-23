package dev.mattramotar.storex.repository.runtime.operations.observation

import dev.mattramotar.storex.result.Result
import kotlinx.coroutines.flow.Flow

interface ObserveAllOperation<Node : Any, Error : Any> {
    fun observeAll(): Flow<Result<List<Node>, Error>>
}