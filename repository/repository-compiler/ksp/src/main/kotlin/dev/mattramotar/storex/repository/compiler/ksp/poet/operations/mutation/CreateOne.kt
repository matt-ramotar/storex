package dev.mattramotar.storex.repository.compiler.ksp.poet.operations.mutation

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import dev.mattramotar.storex.repository.compiler.ksp.extensions.FileSpecExtensions.applyKspOrigins
import dev.mattramotar.storex.repository.compiler.ksp.extensions.TypeSpecExtensions.applyKspOrigins

internal fun createCreateOneFileSpec(
    packageName: String,
    originating: KSFile?
): FileSpec {
    val generatedClass = TypeSpec.classBuilder("CreateOneOperationImpl")
        .addTypeVariable(TypeVariableName("K", ANY))
        .addTypeVariable(TypeVariableName("P", ANY))
        .addTypeVariable(TypeVariableName("N", ANY))
        .addTypeVariable(TypeVariableName("E", ANY))
        .addSuperinterface(
            ClassName("dev.mattramotar.storex.repository.runtime.operations.mutation", "CreateOneOperation")
                .parameterizedBy(
                    TypeVariableName("K"),
                    TypeVariableName("P"),
                    TypeVariableName("N"),
                    TypeVariableName("E")
                )
        )
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(
                    "mutableStore",
                    ClassName("dev.mattramotar.storex.mutablestore.core.api", "MutableStore")
                        .parameterizedBy(TypeVariableName("K"), TypeVariableName("P"), TypeVariableName("N"), TypeVariableName("E"))
                )
                .addParameter(
                    "errorAdapter",
                    LambdaTypeName.get(
                        parameters = listOf(ParameterSpec.unnamed(TypeVariableName("Throwable"))),
                        returnType = TypeVariableName("E")
                    )
                )
                .build()
        )
        .addProperty(
            PropertySpec.builder(
                "mutableStore",
                ClassName("dev.mattramotar.storex.mutablestore.core.api", "MutableStore")
                    .parameterizedBy(TypeVariableName("K"), TypeVariableName("P"), TypeVariableName("N"), TypeVariableName("E"))
            )
                .initializer("mutableStore")
                .addModifiers(KModifier.PRIVATE)
                .build()
        )
        .addProperty(
            PropertySpec.builder(
                "errorAdapter",
                LambdaTypeName.get(
                    parameters = listOf(ParameterSpec.unnamed(TypeVariableName("Throwable"))),
                    returnType = TypeVariableName("E")
                )
            )
                .initializer("errorAdapter")
                .addModifiers(KModifier.PRIVATE)
                .build()
        )
        .addFunction(
            FunSpec.builder("createOne")
                .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                .addParameter("key", TypeVariableName("K"))
                .addParameter("properties", TypeVariableName("P"))
                .returns(
                    ClassName("dev.mattramotar.storex.result", "Result")
                        .parameterizedBy(TypeVariableName("N"), TypeVariableName("E"))
                )
                .addCode(
                    """
                        return try {
                            mutableStore.create(key, properties)
                        } catch (t: Throwable) {
                            Result.Failure(errorAdapter(t))
                        }
                        """.trimIndent()
                )
                .build()
        )
        .build()
        .applyKspOrigins(originating)

    return FileSpec.builder(packageName, "CreateOneOperation")
        .addType(generatedClass)
        .build()
        .applyKspOrigins(originating)
}
