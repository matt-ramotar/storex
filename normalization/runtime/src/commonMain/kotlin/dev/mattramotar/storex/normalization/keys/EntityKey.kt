package dev.mattramotar.storex.normalization.keys

/**
 * Unique, stable identity of a canonical entity in the normalized graph.
 */
data class EntityKey(
    val typeName: String,  // e.g., "User"
    val id: String         // e.g., "123"
) {
    override fun toString(): String = "$typeName:$id"
}
