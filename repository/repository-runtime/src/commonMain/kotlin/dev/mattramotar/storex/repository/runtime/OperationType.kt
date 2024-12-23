package dev.mattramotar.storex.repository.runtime

enum class OperationType {
    // Query
    FindAll,
    FindManyComposite,
    FindOneComposite,
    FindOne,
    QueryManyComposite,
    QueryMany,
    QueryOneComposite,
    QueryOne,

    // Mutation
    DeleteOne,
    CreateOne,
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
    CreateMany
}