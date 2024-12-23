package dev.mattramotar.storex.mutablestore.core.api

class MutationOperations<Key : Any, Partial : Any, Value : Any, Error : Any>(
    private val createOperation: CreateOperation<Key, Partial, Value, Error>,
    private val updateOperation: UpdateOperation<Key, Value, Error>,
    private val deleteOperation: DeleteOperation<Key, Error>
) : CreateOperation<Key, Partial, Value, Error> by createOperation,
    UpdateOperation<Key, Value, Error> by updateOperation,
    DeleteOperation<Key, Error> by deleteOperation