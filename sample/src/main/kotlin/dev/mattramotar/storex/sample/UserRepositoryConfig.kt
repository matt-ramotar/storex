package dev.mattramotar.storex.sample

import dev.mattramotar.storex.repository.runtime.OperationType
import dev.mattramotar.storex.repository.runtime.annotations.RepositoryConfig


@RepositoryConfig(
    name = "UserRepository",
    keyType = User.Key::class,
    propertiesType = User.Properties::class,
    nodeType = User.Node::class,
    compositeType = User.Composite::class,
    operations = [OperationType.FindOneOperation, OperationType.FindAll]
)
object UserRepositoryConfig


