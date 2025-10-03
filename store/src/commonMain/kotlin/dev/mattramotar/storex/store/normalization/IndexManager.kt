package dev.mattramotar.storex.store.normalization

import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.store.StoreKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface IndexManager {
    suspend fun updateIndex(requestKey: StoreKey, roots: List<EntityKey>)
    fun streamIndex(requestKey: StoreKey): Flow<List<EntityKey>?>
}


class InMemoryIndexManager : IndexManager {
    private val lock = Mutex()
    private val map = LinkedHashMap<Long, MutableStateFlow<List<EntityKey>?>>()

    private fun bucket(key: StoreKey): MutableStateFlow<List<EntityKey>?> =
        map.getOrPut(key.stableHash()) { MutableStateFlow(null) }

    override suspend fun updateIndex(requestKey: StoreKey, roots: List<EntityKey>) {
        lock.withLock { bucket(requestKey).value = roots }
    }

    override fun streamIndex(requestKey: StoreKey): Flow<List<EntityKey>?> =
        bucket(requestKey).asStateFlow()
}