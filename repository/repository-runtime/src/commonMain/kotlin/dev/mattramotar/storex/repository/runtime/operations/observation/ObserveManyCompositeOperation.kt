package dev.mattramotar.storex.repository.runtime.operations.observation

import dev.mattramotar.storex.result.Result
import kotlinx.coroutines.flow.Flow

interface ObserveManyCompositeOperation<Key : Any, Composite : Any, Error : Any> {
    fun observeManyComposite(keys: List<Key>): Flow<Result<List<Composite>, Error>>
}