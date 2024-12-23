package dev.mattramotar.storex.store.core.impl

import kotlinx.datetime.Clock

data class CacheEntry<V>(
    val value: V,
    val writeTime: Long = Clock.System.now().toEpochMilliseconds()
)