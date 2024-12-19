package dev.mattramotar.storex.repository.runtime.annotations

import dev.mattramotar.storex.repository.runtime.OperationType
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class RepositoryConfig(
    val name: String,
    val keyType: KClass<*>,
    val nodeType: KClass<*>,
    val operations: Array<OperationType>,
    val errorType: KClass<*> = Throwable::class,
    val queryType: KClass<*> = Any::class,
    val propertiesType: KClass<*> = Any::class,
    val compositeType: KClass<*> = Any::class,
)