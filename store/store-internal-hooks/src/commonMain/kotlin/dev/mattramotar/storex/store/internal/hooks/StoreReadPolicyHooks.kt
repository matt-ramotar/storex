package dev.mattramotar.storex.store.internal.hooks

import kotlinx.coroutines.flow.Flow

interface StoreReadPolicyHooks<Key : Any, Value : Any> {
    fun stream(key: Key, context: ReadPolicyContext): Flow<Value>

    suspend fun get(key: Key, context: ReadPolicyContext): Value?
}