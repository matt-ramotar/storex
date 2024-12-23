package dev.mattramotar.storex.store.extensions.policies.read

import dev.mattramotar.storex.store.extensions.StoreRequest
import kotlinx.coroutines.flow.Flow

/**
 * A "middleware" that intercepts read calls in a Store-like system.
 *
 * - `Key` is the key type (e.g., userId).
 * - `Value` is the data type you’re ultimately streaming or returning.
 *
 * The pipeline model:
 *   - `interceptRead(input, chain)` can either:
 *       a) Return a custom Flow (or skip network fetch).
 *       b) Or call `chain.proceed(input)` to pass control down the pipeline.
 */
interface ReadPolicy<Key : Any, Value : Any> {

    /**
     * This is called for each read (e.g., stream or get).
     * Return a Flow<Value> that either:
     *  - short-circuits the read, or
     *  - calls chain.proceed(input) to continue with the pipeline.
     */
    suspend fun interceptRead(
        request: StoreRequest<Key, Value>,
        chain: Chain<Key, Value>
    ): Flow<Value>

    /**
     * Represents the next policy in the chain.
     * `chain.proceed(input)` calls the next policy,
     * or the final read implementation if none remain.
     */
    interface Chain<Key : Any, Value : Any> {
        suspend fun proceed(request: StoreRequest<Key, Value>): Flow<Value?>
    }
}


