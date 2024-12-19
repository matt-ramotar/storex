package dev.mattramotar.storex.repository.runtime.operations.bulk

import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result

interface DeleteAllOperation<Error> {
    suspend fun deleteAll(dataSources: DataSources? = null): Result<Int, Error>
}