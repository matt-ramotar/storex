package dev.mattramotar.storex.store.core.api

import kotlinx.coroutines.flow.Flow

interface SourceOfTruth<Key : Any, Value : Any> {
    fun read(key: Key): Flow<Value?>
    suspend fun write(key: Key, value: Value)
    suspend fun delete(key: Key)
    suspend fun deleteAll()
}