package dev.mattramotar.storex.repository.runtime.extensions

import dev.mattramotar.storex.repository.runtime.OperationType

object OperationTypeExtensions {
    fun List<OperationType>.needsStore(): Boolean =
        this.contains(OperationType.FindAll)
            || this.contains(OperationType.FindOne)
            || this.contains(OperationType.QueryOne)
            || this.contains(OperationType.QueryMany)
            || this.contains(OperationType.ObserveAll)
            || this.contains(OperationType.ObserveMany)
            || this.contains(OperationType.ObserveOne)

    fun List<OperationType>.needsCompositeStore(): Boolean =
        this.contains(OperationType.FindOneComposite)
            || this.contains(OperationType.FindManyComposite)
            || this.contains(OperationType.QueryOneComposite)
            || this.contains(OperationType.QueryManyComposite)
            || this.contains(OperationType.ObserveOneComposite)
            || this.contains(OperationType.ObserveManyComposite)

    fun List<OperationType>.needsMutableStore(): Boolean =
        this.contains(OperationType.DeleteOne)
            || this.contains(OperationType.UpdateOne)
            || this.contains(OperationType.CreateOne)
            || this.contains(OperationType.ReplaceOne)
            || this.contains(OperationType.UpsertOne)
            || this.contains(OperationType.DeleteAll)
            || this.contains(OperationType.DeleteMany)
            || this.contains(OperationType.CreateMany)
}