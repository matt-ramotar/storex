package dev.mattramotar.storex.core.utils

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.internal.SourceOfTruth
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Fake SourceOfTruth for testing.
 *
 * Provides an in-memory SoT with full control over emissions for testing.
 * Tracks all write and delete operations for assertions.
 */
class FakeSourceOfTruth<K : StoreKey, V : Any> : SourceOfTruth<K, V, V> {
    private val data = mutableMapOf<K, V>()
    private val flows = mutableMapOf<K, MutableSharedFlow<V?>>()

    // Recording lists for test assertions
    val writes = mutableListOf<Pair<K, V>>()
    val deletes = mutableListOf<K>()
    val rekeys = mutableListOf<Pair<K, K>>()
    var transactionCount = 0
        private set

    // Error injection
    var throwOnRead: Throwable? = null
    var throwOnWrite: Throwable? = null
    var throwOnDelete: Throwable? = null

    override fun reader(key: K): Flow<V?> {
        throwOnRead?.let { throw it }
        return flows.getOrPut(key) {
            MutableSharedFlow<V?>(
                replay = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            ).also { flow ->
                // Emit current value immediately
                flow.tryEmit(data[key])
            }
        }
    }

    override suspend fun write(key: K, value: V) {
        throwOnWrite?.let { throw it }
        writes.add(key to value)
        data[key] = value

        // Emit to flow if it exists
        flows[key]?.emit(value)
    }

    override suspend fun delete(key: K) {
        throwOnDelete?.let { throw it }
        deletes.add(key)
        data.remove(key)

        // Emit null to flow if it exists
        flows[key]?.emit(null)
    }

    override suspend fun withTransaction(block: suspend () -> Unit) {
        transactionCount++
        block()
    }

    override suspend fun rekey(
        old: K,
        new: K,
        reconcile: suspend (oldRead: V, serverRead: V?) -> V
    ) {
        rekeys.add(old to new)
        val oldValue = data.remove(old)
        if (oldValue != null) {
            val reconciled = reconcile(oldValue, data[new])
            data[new] = reconciled
            flows[new]?.emit(reconciled)
        }
    }

    /**
     * Manually emit a value to a key's flow (simulating external update).
     */
    suspend fun emit(key: K, value: V?) {
        if (value != null) {
            data[key] = value
        } else {
            data.remove(key)
        }
        flows[key]?.emit(value)
    }

    /**
     * Get current data (for test assertions).
     */
    fun getData(key: K): V? = data[key]

    /**
     * Get all data (for test assertions).
     */
    fun getAllData(): Map<K, V> = data.toMap()

    /**
     * Clear all data and recordings.
     */
    fun clear() {
        data.clear()
        flows.clear()
        writes.clear()
        deletes.clear()
        rekeys.clear()
        transactionCount = 0
        throwOnRead = null
        throwOnWrite = null
        throwOnDelete = null
    }
}
