package dev.mattramotar.storex.core.seams

import dev.mattramotar.storex.core.StoreKey
import kotlinx.coroutines.flow.Flow

interface SourceOfTruth<K : StoreKey, ReadDb, WriteDb> {
    fun reader(key: K): Flow<ReadDb?>

    suspend fun write(key: K, value: WriteDb)

    suspend fun delete(key: K)

    suspend fun withTransaction(block: suspend () -> Unit)

    suspend fun rekey(old: K, new: K, reconcile: suspend (oldRead: ReadDb, serverRead: ReadDb?) -> ReadDb) {
    }

    suspend fun clearCache(key: K) {
    }
}
