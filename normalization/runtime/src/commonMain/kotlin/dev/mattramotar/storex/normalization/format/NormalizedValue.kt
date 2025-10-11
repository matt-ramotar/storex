package dev.mattramotar.storex.normalization.format

import dev.mattramotar.storex.normalization.keys.EntityKey

sealed class NormalizedValue {
    /** Primitives: String, Boolean, Double, Long, null, etc. */
    data class Scalar(val value: Any?) : NormalizedValue()

    /** Reference to another entity (1:1 or many:1). */
    data class Ref(val key: EntityKey) : NormalizedValue()

    /** Homogeneous list of primitives. */
    data class ScalarList(val values: List<Any?>) : NormalizedValue()

    /** Homogeneous list of references (1:many or many:many). */
    data class RefList(val keys: List<EntityKey>) : NormalizedValue()

    /**
     * Represents a structurally embedded object stored inline.
     * This keeps the IR clean, structured, and serialization-agnostic.
     */
    data class EmbeddedObject(val fields: NormalizedRecord) : NormalizedValue()
}

/** Canonical flattened record of a single entity. */
typealias NormalizedRecord = Map<String, NormalizedValue>
