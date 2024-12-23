package dev.mattramotar.storex.repository.runtime.annotations

import dev.mattramotar.storex.repository.runtime.OperationType
import kotlin.reflect.KClass


/**
 * Describes a repository to generate, specifying which operations to implement.
 *
 * @sample PostRepositoryConfig
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class RepositoryConfig(
    val name: String,
    val keyType: KClass<*>,
    val nodeType: KClass<*>,
    val propertiesType: KClass<*> = Any::class,
    val compositeType: KClass<*> = Any::class,
    val errorType: KClass<*> = Throwable::class,
    val operations: Array<OperationType>
)

private data object PostKey
private data object PostNode
private data object PostPartial
private data object PostComposite
private data object PostError

@RepositoryConfig(
    name = "PostRepository",
    keyType = PostKey::class,
    nodeType = PostNode::class,
    propertiesType = PostPartial::class,
    compositeType = PostComposite::class,
    errorType = PostError::class,
    operations = [
        OperationType.FindOne,
        OperationType.FindOneComposite,
        OperationType.CreateOne,
        OperationType.UpdateOne
    ]
)
private object PostRepositoryConfig