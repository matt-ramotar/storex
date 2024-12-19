package dev.mattramotar.storex.repository.runtime.operations.bulk

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface DeleteManyOperation<Key, Error> {
    suspend fun deleteMany(keys: List<Key>, dataSources: DataSources? = null): Result<Int, Error>
}