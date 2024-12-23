package dev.mattramotar.storex.repository.compiler.ksp.extensions

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import kotlin.reflect.KClass

internal object KSAnnotationExtensions {

    /**
     * Fetches a string argument from a KSAnnotation by name.
     */
    fun KSAnnotation.findStringArgument(name: String): String? {
        return this.arguments.firstOrNull { it.name?.asString() == name }?.value as? String
    }

    /**
     * Fetches a KSType argument from a KSAnnotation by name.
     */
    fun KSAnnotation.findKSTypeArgument(name: String): KSType? {
        return this.arguments.firstOrNull { it.name?.asString() == name }?.value as? KSType
    }

    /**
     * Fetches an enum list argument from a KSAnnotation by name, mapping them to real enum constants.
     */
    fun <E : Enum<E>> KSAnnotation.findEnumListArgument(
        name: String,
        enumClass: KClass<E>
    ): List<E> {
        val rawList = this.arguments.firstOrNull { it.name?.asString() == name }?.value as? List<*> ?: return emptyList()
        return rawList.mapNotNull { enumValue ->
            if (enumValue is KSType) {
                val enumName = enumValue.declaration.simpleName.asString()
                enumClass.java.enumConstants?.firstOrNull { it.name == enumName }
            } else null
        }
    }
}