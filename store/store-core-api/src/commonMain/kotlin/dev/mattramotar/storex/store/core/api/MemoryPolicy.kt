package dev.mattramotar.storex.store.core.api

class MemoryPolicy<Key : Any, Value : Any>(
    val maxSize: Int = 100,
    val expireAfterWriteMillis: Long? = null
)