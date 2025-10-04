package dev.mattramotar.storex.store.normalization

import dev.mattramotar.storex.normalization.keys.EntityKey
import dev.mattramotar.storex.store.internal.StoreException

/**
 * Exception thrown when graph composition fails.
 *
 * Provides detailed diagnostic information including which entities failed,
 * partial composition state, and traversal metadata.
 */
class GraphCompositionException(
    message: String,
    /** The root entity key that was being composed. */
    val rootKey: EntityKey,
    /** The shape ID used for composition. */
    val shapeId: ShapeId,
    /** Number of records successfully composed before failure. */
    val partialRecords: Int,
    /** Total number of records expected (if known). */
    val totalExpected: Int? = null,
    /** Map of entity keys to their specific failure reasons. */
    val failedEntities: Map<EntityKey, Throwable> = emptyMap(),
    /** Whether maximum traversal depth was reached. */
    val maxDepthReached: Boolean = false,
    cause: Throwable? = null
) : StoreException(message, cause) {

    override val isRetryable: Boolean
        get() = failedEntities.values.any {
            (it as? StoreException)?.isRetryable == true
        }

    override fun toString(): String = buildString {
        appendLine("GraphCompositionException: $message")
        appendLine("  Root: $rootKey (shape: ${shapeId.value})")
        appendLine("  Progress: $partialRecords${totalExpected?.let { "/$it" } ?: ""} records composed")

        if (failedEntities.isNotEmpty()) {
            appendLine("  Failed entities:")
            failedEntities.forEach { (key, error) ->
                appendLine("    - $key: ${error::class.simpleName}: ${error.message}")
            }
        }

        if (maxDepthReached) {
            appendLine("  ⚠️ Maximum traversal depth reached")
        }

        cause?.let {
            appendLine("  Caused by: ${it::class.simpleName}: ${it.message}")
        }
    }
}
