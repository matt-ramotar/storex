package dev.mattramotar.storex.repository.runtime.operations.observation

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result
import kotlinx.coroutines.flow.Flow

interface ObserveManyCompositeOperation<Key, Composite, Error> {
    fun observeManyComposite(keys: List<Key>, dataSources: DataSources): Flow<Result<List<Composite>, Error>>
}