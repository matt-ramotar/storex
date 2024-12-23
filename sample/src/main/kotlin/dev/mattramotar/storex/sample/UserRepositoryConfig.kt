package dev.mattramotar.storex.sample

import dev.mattramotar.storex.repository.runtime.OperationType
import dev.mattramotar.storex.repository.runtime.annotations.RepositoryConfig


@RepositoryConfig(
    name = "UserRepository",
    keyType = User.Key::class,
    propertiesType = User.Properties::class,
    nodeType = User.Node::class,
    compositeType = User.Composite::class,
    errorType = CustomError::class,
    operations = [OperationType.FindOne, OperationType.CreateOne, OperationType.UpdateOne, OperationType.FindOneComposite]
)
object UserRepositoryConfig


