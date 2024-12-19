package dev.mattramotar.storex.repository.runtime.operations.observation

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result
import kotlinx.coroutines.flow.Flow

interface ObserveOneCompositeOperation<Key, Composite, Error> {
    fun observeOneComposite(key: Key, dataSources: DataSources): Flow<Result<Composite?, Error>>
}