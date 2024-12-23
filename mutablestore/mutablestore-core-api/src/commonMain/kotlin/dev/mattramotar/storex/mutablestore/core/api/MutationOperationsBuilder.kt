package dev.mattramotar.storex.mutablestore.core.api

import dev.mattramotar.storex.result.Result

class MutationOperationsBuilder<Key : Any, Partial : Any, Value : Any, Error : Any> {
    private var createOperation: CreateOperation<Key, Partial, Value, Error> = CreateOperation { _, _ -> Result.NoOp("Not implemented") }
    private var updateOperation: UpdateOperation<Key, Value, Error> = UpdateOperation { _, _ -> Result.NoOp("Not implemented") }
    private var deleteOperation: DeleteOperation<Key, Error> = DeleteOperation { _ -> Result.NoOp("Not implemented") }

    fun createOperation(operation: CreateOperation<Key, Partial, Value, Error>) = apply {
        this.createOperation = operation
    }

    fun updateOperation(operation: UpdateOperation<Key, Value, Error>) = apply {
        this.updateOperation = operation
    }

    fun deleteOperation(operation: DeleteOperation<Key, Error>) = apply {
        this.deleteOperation = operation
    }

    fun build(): MutationOperations<Key, Partial, Value, Error> =
        MutationOperations(createOperation, updateOperation, deleteOperation)
}