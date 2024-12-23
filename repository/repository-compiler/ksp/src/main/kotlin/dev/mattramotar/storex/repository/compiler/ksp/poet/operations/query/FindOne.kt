package dev.mattramotar.storex.repository.compiler.ksp.poet.operations.query

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

internal fun createFindOneOperationFileSpec(
    packageName: String,
    originating: KSFile?
): FileSpec {

    val generatedClass = TypeSpec.classBuilder("FindOneOperationImpl")
        .addTypeVariable(TypeVariableName("K", ANY))
        .addTypeVariable(TypeVariableName("N", ANY))
        .addTypeVariable(TypeVariableName("E", ANY))
        .addSuperinterface(
            ClassName("dev.mattramotar.storex.repository.runtime.operations.query", "FindOneOperation")
                .parameterizedBy(TypeVariableName("K"), TypeVariableName("N"), TypeVariableName("E"))
        )
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(
                    "store",
                    ClassName("dev.mattramotar.storex.store.core.api", "Store")
                        .parameterizedBy(TypeVariableName("K"), TypeVariableName("N"))
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
                "store",
                ClassName("dev.mattramotar.storex.store.core.api", "Store")
                    .parameterizedBy(TypeVariableName("K"), TypeVariableName("N"))
            )
                .initializer("store")
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
            FunSpec.builder("findOne")
                .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                .addParameter("key", TypeVariableName("K"))
                .returns(
                    ClassName("dev.mattramotar.storex.result", "Result")
                        .parameterizedBy(TypeVariableName("N"), TypeVariableName("E"))
                )
                .addCode(
                    """
                        return try {
                            val data = store.get(key)
                            if (data == null) {
                                Result.Failure(
                                    errorAdapter(Throwable("No data for key=${'$'}%N"))
                                )
                            } else {
                                Result.Success(data)
                            }
                        } catch (t: Throwable) {
                            Result.Failure(errorAdapter(t))
                        }
                        """.trimIndent(),
                    "key"
                )
                .build()
        )
        .build()
        .applyKspOrigins(originating)

    return FileSpec.builder(packageName, "FindOneOperation")
        .addType(generatedClass)
        .build()
        .applyKspOrigins(originating)
}
