package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.seams.fetcherOf as seamFetcherOf
import dev.mattramotar.storex.core.seams.streamingFetcherOf as seamStreamingFetcherOf
import kotlinx.coroutines.flow.Flow

typealias Fetcher<Key, Network> = dev.mattramotar.storex.core.seams.Fetcher<Key, Network>
typealias FetchRequest = dev.mattramotar.storex.core.seams.FetchRequest
typealias Urgency = dev.mattramotar.storex.core.seams.Urgency
typealias ConditionalRequest = dev.mattramotar.storex.core.seams.ConditionalRequest
typealias FetcherResult<T> = dev.mattramotar.storex.core.seams.FetcherResult<T>

fun <Key : StoreKey, Network : Any> fetcherOf(
    fetch: suspend (Key) -> Network
): Fetcher<Key, Network> {
    val delegate = seamFetcherOf(fetch)
    return delegate
}

fun <Key : StoreKey, Network : Any> streamingFetcherOf(
    fetch: (Key) -> Flow<Network>
): Fetcher<Key, Network> {
    val delegate = seamStreamingFetcherOf(fetch)
    return delegate
}
