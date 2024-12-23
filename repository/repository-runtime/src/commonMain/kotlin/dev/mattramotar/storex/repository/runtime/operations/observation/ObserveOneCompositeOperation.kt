package dev.mattramotar.storex.repository.runtime.operations.observation

import dev.mattramotar.storex.result.Result
import kotlinx.coroutines.flow.Flow

interface ObserveOneCompositeOperation<Key : Any, Composite : Any, Error : Any> {
    fun observeOneComposite(key: Key): Flow<Result<Composite?, Error>>
}