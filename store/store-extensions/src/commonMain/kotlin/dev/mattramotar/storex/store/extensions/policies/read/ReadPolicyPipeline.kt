package dev.mattramotar.storex.store.extensions.policies.read

import dev.mattramotar.storex.store.extensions.StoreRequest
import kotlinx.coroutines.flow.Flow

class ReadPolicyPipeline<Key : Any, Value : Any>(
    private val policies: List<ReadPolicy<Key, Value>>,
    private val finalRead: suspend (StoreRequest<Key, Value>) -> Flow<Value?>
) {
    suspend fun start(request: StoreRequest<Key, Value>): Flow<Value?> {
        var index = -1
        val chain = object : ReadPolicy.Chain<Key, Value> {
            override suspend fun proceed(request: StoreRequest<Key, Value>): Flow<Value?> {
                index++
                return if (index < policies.size) {
                    policies[index].interceptRead(request, this)
                } else {
                    // No more policies, call the final read logic
                    finalRead(request)
                }
            }
        }
        return chain.proceed(request)
    }
}
