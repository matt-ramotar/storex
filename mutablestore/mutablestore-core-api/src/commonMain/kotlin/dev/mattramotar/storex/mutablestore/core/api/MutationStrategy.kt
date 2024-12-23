package dev.mattramotar.storex.mutablestore.core.api

/**
 * Represents a pure function that intercepts a [Mutation] in a pipeline to transform or handle it.
 *
 * Each [MutationStrategy] can:
 * - Inspect the mutation and any read-only metadata.
 * - Transform the mutation.
 * - Fail/queue the mutation if conflict resolution is not possible.
 *
 * Strategies must not call the network or write to the SOT directly, preserving unidirectional flow.
 */
interface MutationStrategy<Key, Partial, Value, Error> {

    /**
     * Intercepts a [mutation] and decides whether to transform, fail, or pass it along to the rest of the chain.
     *
     * @param mutation The input mutation (create, update, or delete).
     * @param chain Provides a way to continue the pipeline.
     * @return An [Outcome] describing how the pipeline should continue or terminate.
     */
    suspend fun intercept(
        mutation: Mutation<Key, Partial, Value>,
        chain: Chain<Key, Partial, Value, Error>
    ): Outcome<Key, Partial, Value, Error>

    /**
     * Represents the next stage in the mutation pipeline. Calling [proceed] continues the chain.
     */
    interface Chain<Key, Partial, Value, Error> {
        /**
         * Continues execution of the pipeline with the given [mutation].
         */
        suspend fun proceed(mutation: Mutation<Key, Partial, Value>): Outcome<Key, Partial, Value, Error>
    }

    /**
     * Possible outcomes of a [MutationStrategy] intercept.
     */
    sealed class Outcome<out Key, out Partial, out Value, out Error> {

        /**
         * Continues the mutation pipeline with the provided [mutation].
         */
        data class Continue<Key, Partial, Value, Error>(
            val mutation: Mutation<Key, Partial, Value>
        ) : Outcome<Key, Partial, Value, Error>()

        /**
         * Fails the mutation pipeline with the given [error].
         */
        data class Fail<Key, Value, Error>(
            val error: Error
        ) : Outcome<Key, Nothing, Value, Error>()

        /**
         * Exits the mutation pipeline with no further actions.
         *
         * @param reason Optional rationale for why this outcome occurred.
         */
        data class NoOp<Key, Value, Error>(
            val reason: String? = null
        ) : Outcome<Key, Nothing, Value, Error>()
    }
}