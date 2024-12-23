package dev.mattramotar.storex.store.internal.hooks

data class ReadPolicyContext(
    val forceNetworkFetch: Boolean = false,
    val skipMemoryCache: Boolean = false,
    val skipSourceOfTruth: Boolean = false,
    val fallbackToSOT: Boolean = false,
)