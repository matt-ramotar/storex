package dev.mattramotar.storex.mutablestore.core.api

/**
 *
 * A MutationStrategy is a pure function that intercepts a Mutation plus read-only Metadata—and decides whether to pass the mutation on, transform it, or fail/queue it. It cannot call the network or SOT directly, preserving unidirectional flow.
 *
 * StoreX runs a pipeline (chain) of Mutation Strategies. Each strategy can:
 * Inspect the mutation and metadata (like offline status).
 * Transform the mutation or attach flags (e.g., queue if offline).
 * Stop the mutation or fail early (e.g., conflict cannot be resolved).
 * Or simply pass it through.
 *
 * Composability of mutation strategies (offline queueing, conflict resolution, batching, etc.) without letting them directly perform network or SOT writes (preserves unidirectional data flow and avoids redundant calls).
 *
 */
interface MutationStrategy<Key, Partial, Value, Error> {
    suspend fun intercept(
        mutation: Mutation<Key, Partial, Value>,
        chain: Chain<Key, Partial, Value, Error>
    ): Outcome<Key, Partial, Value, Error>

    interface Chain<Key, Partial, Value, Error> {
        suspend fun proceed(mutation: Mutation<Key, Partial, Value>): Outcome<Key, Partial, Value, Error>
    }

    sealed class Outcome<out Key, out Partial, out Value, out Error> {
        data class Continue<Key, Partial, Value, Error>(val mutation: Mutation<Key, Partial, Value>) :
            Outcome<Key, Partial, Value, Error>()

        data class Fail<Key, Value, Error>(val error: Error) : Outcome<Key, Nothing, Value, Error>()
        data class NoOp<Key, Value, Error>(val reason: String? = null) : Outcome<Key, Nothing, Value, Error>()
    }
}