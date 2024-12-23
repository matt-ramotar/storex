package dev.mattramotar.storex.mutablestore.core.api

sealed class Mutation<out Key, out Partial, out Value> {
    data class Create<Key, Partial>(val key: Key, val partial: Partial) : Mutation<Key, Partial, Nothing>()
    data class Update<Key, Value>(val key: Key, val value: Value) : Mutation<Key, Nothing, Value>()
    data class Delete<Key>(val key: Key) : Mutation<Key, Nothing, Nothing>()
}