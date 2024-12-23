package dev.mattramotar.storex.mutablestore.core

import dev.mattramotar.storex.mutablestore.core.api.MutableStore
import kotlin.jvm.JvmName


@JvmName("mutableStoreAllParamsWithBuilder")
inline fun <Key : Any, Partial : Any, Value : Any, Error : Any> mutableStore(builder: MutableStoreBuilder<Key, Partial, Value, Error>.() -> Unit):
    MutableStore<Key, Partial, Value, Error> {
    return MutableStoreBuilder<Key, Partial, Value, Error>().apply(builder).build()
}

@JvmName("mutableStoreErrorWithBuilder")
inline fun <Key : Any, Value : Any, Error : Any> mutableStore(builder: MutableStoreBuilder<Key, Value, Value, Error>.() -> Unit):
    MutableStore<Key, Value, Value, Error> {
    return MutableStoreBuilder<Key, Value, Value, Error>().apply(builder).build()
}

@JvmName("mutableStorePartialWithBuilder")
inline fun <Key : Any, Partial : Any, Value : Any> mutableStore(builder: MutableStoreBuilder<Key, Partial, Value, Throwable>.() -> Unit):
    MutableStore<Key, Partial, Value, Throwable> {
    return MutableStoreBuilder<Key, Partial, Value, Throwable>().apply(builder).build()
}


@JvmName("mutableStoreWithBuilder")
inline fun <Key : Any, Value : Any> mutableStore(builder: MutableStoreBuilder<Key, Value, Value, Throwable>.() -> Unit):
    MutableStore<Key, Value, Value, Throwable> {
    return MutableStoreBuilder<Key, Value, Value, Throwable>().apply(builder).build()
}