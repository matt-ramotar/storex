package dev.mattramotar.storex.repository.runtime

enum class OperationType {
    // Query
    FindAll,
    FindManyComposite,
    FindOneComposite,
    FindOneOperation,
    QueryManyComposite,
    QueryMany,
    QueryOneComposite,
    QueryOne,

    // Mutation
    DeleteOne,
    InsertOne,
    ReplaceOne,
    UpdateOne,
    UpsertOne,

    // Observation
    ObserveAll,
    ObserveManyComposite,
    ObserveMany,
    ObserveOneComposite,
    ObserveOne,

    // Bulk
    DeleteAll,
    DeleteMany,
    InsertMany
}