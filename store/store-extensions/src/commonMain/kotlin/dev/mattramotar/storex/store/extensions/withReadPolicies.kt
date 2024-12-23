@file:Suppress("UNCHECKED_CAST")

package dev.mattramotar.storex.store.extensions

import dev.mattramotar.storex.store.core.api.Store
import dev.mattramotar.storex.store.extensions.policies.read.ReadPolicy
import dev.mattramotar.storex.store.extensions.policies.read.ReadPolicyPipeline
import dev.mattramotar.storex.store.internal.hooks.ReadPolicyContext
import dev.mattramotar.storex.store.internal.hooks.StoreReadPolicyHooks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

/**
 * Wraps a Store<Key, Value> in a new Store<StoreRequest<Key, Value>, Value>,
 * applying the provided read policies in a pipeline before delegating reads.
 *
 * Note: In the snippet above, we gather all policies from input.readPolicies + policies. That allows for:
 *
 * Global or method-level policies via vararg policies.
 * Per-request policies stored in input.readPolicies.
 * Feel free to customize how you combine them (only input.readPolicies, only the function’s policies, etc.).
 */
fun <Key : Any, Value : Any> Store<Key, Value>.withReadPolicies(
    vararg policies: ReadPolicy<Key, Value>
): Store<StoreRequest<Key, Value>, Value> {
    // The underlying store that we delegate to for final reads (or writes).
    val delegate = this

    return object : Store<StoreRequest<Key, Value>, Value> {

        override fun stream(key: StoreRequest<Key, Value>): Flow<Value> = flow {
            // 1) Construct the pipeline with the user’s policies
            val pipeline = ReadPolicyPipeline(
                policies = key.readPolicies + policies, // Combine request-level + method-level
                finalRead = { req ->
                    flow {

                        val readPolicyHooks = delegate as StoreReadPolicyHooks<Key, Value>

                        emitAll(
                            readPolicyHooks.stream(
                                req.key, ReadPolicyContext(
                                    forceNetworkFetch = req.forceNetworkFetch,
                                    fallbackToSOT = req.fallbackToSOT,
                                    skipMemoryCache = req.skipMemoryCache,
                                    skipSourceOfTruth = req.skipSourceOfTruth
                                )
                            )
                        )

                    }
                }
            )

            // 2) Start the pipeline with the user’s request
            emitAll(pipeline.start(key).filterNotNull())
        }

        override suspend fun get(key: StoreRequest<Key, Value>): Value? {
            // 1) Build a pipeline
            val pipeline = ReadPolicyPipeline(
                policies = key.readPolicies + policies,
                finalRead = { req ->
                    // The final read: delegate to the original store’s get
                    flow {

                        val readPolicyHooks = delegate as StoreReadPolicyHooks<Key, Value>

                        val data = readPolicyHooks.get(
                            req.key, ReadPolicyContext(
                                forceNetworkFetch = req.forceNetworkFetch,
                                fallbackToSOT = req.fallbackToSOT,
                                skipMemoryCache = req.skipMemoryCache,
                                skipSourceOfTruth = req.skipSourceOfTruth
                            )
                        )


                        data?.let { emit(it) }
                    }
                }
            )

            // 2) Convert the pipeline Flow to a single value
            return pipeline.start(key).firstOrNull()
        }

        /**
         * Pass-through invalidation and clearing calls to the original store.
         * Typically these don't need to be intercepted by a read policy,
         * but if you want them to be, you could adapt similarly.
         */
        override suspend fun invalidate(key: StoreRequest<Key, Value>) {
            delegate.invalidate(key.key)
        }

        override suspend fun clear(key: StoreRequest<Key, Value>) {
            delegate.clear(key.key)
        }

        override suspend fun clearAll() {
            delegate.clearAll()
        }
    }
}
