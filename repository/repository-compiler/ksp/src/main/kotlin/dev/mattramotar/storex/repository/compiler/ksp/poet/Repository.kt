package dev.mattramotar.storex.repository.compiler.ksp.poet

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import dev.mattramotar.storex.repository.compiler.ksp.extensions.FileSpecExtensions.applyKspOrigins
import dev.mattramotar.storex.repository.compiler.ksp.extensions.TypeSpecExtensions.applyKspOrigins
import dev.mattramotar.storex.repository.runtime.OperationType
import dev.mattramotar.storex.repository.runtime.extensions.OperationTypeExtensions.needsCompositeStore
import dev.mattramotar.storex.repository.runtime.extensions.OperationTypeExtensions.needsMutableStore
import dev.mattramotar.storex.repository.runtime.extensions.OperationTypeExtensions.needsStore


/**
 * Generate the final repository interface and a builder class to “wire” them.
 *
 * Example approach: the repository interface might extend only the operation
 * interfaces the user requested. Or we could create a single class that holds
 * references to each generated operation. You can adapt as needed.
 */
internal fun createRepositoryInterfaceAndBuilder(
    pkg: String,
    repoName: String,
    operationTypes: List<OperationType>,
    keyType: TypeName,
    nodeType: TypeName,
    propertiesType: TypeName,
    compositeType: TypeName,
    errorType: TypeName,
    originating: KSFile?
): FileSpec {

    // The interface name: e.g. "PostRepository"
    val interfaceBuilder = TypeSpec.interfaceBuilder(repoName)

    fun operationPackageName(pkg: String) = "dev.mattramotar.storex.repository.runtime.operations.$pkg"

    // For each operation the user wants, add a "superinterface" that matches
    // the corresponding operation interface with the appropriate type parameters.
    if (OperationType.FindOne in operationTypes) {
        interfaceBuilder.addSuperinterface(
            ClassName(operationPackageName("query"), "FindOneOperation")
                .parameterizedBy(keyType, nodeType, errorType)
        )
    }
    if (OperationType.FindOneComposite in operationTypes) {
        interfaceBuilder.addSuperinterface(
            ClassName(operationPackageName("query"), "FindOneCompositeOperation")
                .parameterizedBy(keyType, compositeType, errorType)
        )
    }
    if (OperationType.UpdateOne in operationTypes) {
        interfaceBuilder.addSuperinterface(
            ClassName(operationPackageName("mutation"), "UpdateOneOperation")
                .parameterizedBy(keyType, nodeType, errorType)
        )
    }
    if (OperationType.CreateOne in operationTypes) {
        interfaceBuilder.addSuperinterface(
            ClassName(operationPackageName("mutation"), "CreateOneOperation")
                .parameterizedBy(keyType, propertiesType, nodeType, errorType)
        )
    }

    val repoInterface = interfaceBuilder
        .build()
        .applyKspOrigins(originating)

    // A builder class that constructs an instance of $repoName using all generated impl classes
    val builderClassName = "${repoName}Builder"
    val builderClass = TypeSpec.classBuilder(builderClassName)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .apply {
                    if (operationTypes.needsStore()) {
                        addParameter(
                            "store",
                            ClassName("dev.mattramotar.storex.store.core.api", "Store")
                                .parameterizedBy(keyType, nodeType)
                        )
                    }

                    if (operationTypes.needsCompositeStore()) {
                        addParameter(
                            "compositeStore",
                            ClassName("dev.mattramotar.storex.store.core.api", "Store")
                                .parameterizedBy(keyType, compositeType)
                        )
                    }

                    if (operationTypes.needsMutableStore()) {
                        addParameter(
                            "mutableStore",
                            ClassName("dev.mattramotar.storex.mutablestore.core.api", "MutableStore")
                                .parameterizedBy(keyType, propertiesType, nodeType, errorType)
                        )
                    }
                }
                .addParameter(
                    "errorAdapter",
                    LambdaTypeName.get(
                        parameters = listOf(ParameterSpec.unnamed(TypeVariableName("Throwable"))),
                        returnType = errorType
                    )
                )
                .build()
        )

        .apply {
            if (operationTypes.needsStore()) {
                addProperty(
                    PropertySpec.builder(
                        "store",
                        ClassName("dev.mattramotar.storex.store.core.api", "Store")
                            .parameterizedBy(keyType, nodeType)
                    )
                        .initializer("store")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )
            }

            if (operationTypes.needsCompositeStore()) {
                addProperty(
                    PropertySpec.builder(
                        "compositeStore",
                        ClassName("dev.mattramotar.storex.store.core.api", "Store")
                            .parameterizedBy(keyType, compositeType)
                    )
                        .initializer("compositeStore")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )
            }

            if (operationTypes.needsMutableStore()) {
                addProperty(
                    PropertySpec.builder(
                        "mutableStore",
                        ClassName("dev.mattramotar.storex.mutablestore.core.api", "MutableStore")
                            .parameterizedBy(keyType, propertiesType, nodeType, errorType)
                    )
                        .initializer("mutableStore")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )
            }
        }
        .addProperty(
            PropertySpec.builder(
                "errorAdapter",
                LambdaTypeName.get(
                    parameters = listOf(ParameterSpec.unnamed(TypeVariableName("Throwable"))),
                    returnType = errorType
                )
            )
                .initializer("errorAdapter")
                .addModifiers(KModifier.PRIVATE)
                .build()
        )
        .apply {

            when {
                operationTypes.needsStore() && operationTypes.needsMutableStore() && operationTypes.needsCompositeStore() -> {
                    addFunction(
                        FunSpec.builder("build")
                            .returns(ClassName(pkg, repoName))
                            .addCode(
                                """
                        return ${repoName}Impl(
                            errorAdapter,
                            store,
                            compositeStore,
                            mutableStore,
                        )
                        """.trimIndent()
                            )
                            .build()
                    )
                }

                operationTypes.needsStore() && operationTypes.needsMutableStore() -> {
                    addFunction(
                        FunSpec.builder("build")
                            .returns(ClassName(pkg, repoName))
                            .addCode(
                                """
                        return ${repoName}Impl(
                            errorAdapter,
                            store,
                            mutableStore
                        )
                        """.trimIndent()
                            )
                            .build()
                    )
                }

                operationTypes.needsStore() && operationTypes.needsCompositeStore() -> {
                    addFunction(
                        FunSpec.builder("build")
                            .returns(ClassName(pkg, repoName))
                            .addCode(
                                """
                        return ${repoName}Impl(
                            errorAdapter,                        
                            store,
                            compositeStore,
                        )
                        """.trimIndent()
                            )
                            .build()
                    )
                }

                operationTypes.needsMutableStore() && operationTypes.needsCompositeStore() -> {
                    addFunction(
                        FunSpec.builder("build")
                            .returns(ClassName(pkg, repoName))
                            .addCode(
                                """
                        return ${repoName}Impl(
                            errorAdapter,                        
                            compositeStore,
                            mutableStore,
                        )
                        """.trimIndent()
                            )
                            .build()
                    )
                }

                else -> {
                    addFunction(
                        FunSpec.builder("build")
                            .returns(ClassName(pkg, repoName))
                            .addCode(
                                """
                        return ${repoName}Impl(
                            errorAdapter,                        
                            store,
                        )
                        """.trimIndent()
                            )
                            .build()
                    )
                }
            }
        }
        .build()
        .applyKspOrigins(originating)

    // A concrete impl of the repo interface that delegates to the generated operations
    val generatedImpl = TypeSpec.classBuilder("${repoName}Impl")
        .addSuperinterface(ClassName(pkg, repoName))
        .primaryConstructor(
            FunSpec.constructorBuilder()

                .addParameter(
                    "errorAdapter",
                    LambdaTypeName.get(
                        parameters = listOf(ParameterSpec.unnamed(TypeVariableName("Throwable"))),
                        returnType = errorType
                    )
                )
                .apply {
                    if (operationTypes.needsStore()) {
                        addParameter(
                            "store",
                            ClassName("dev.mattramotar.storex.store.core.api", "Store")
                                .parameterizedBy(keyType, nodeType)
                        )
                    }

                    if (operationTypes.needsCompositeStore()) {
                        addParameter(
                            "compositeStore",
                            ClassName("dev.mattramotar.storex.store.core.api", "Store")
                                .parameterizedBy(keyType, compositeType)
                        )
                    }

                    if (operationTypes.needsMutableStore()) {
                        addParameter(
                            "mutableStore",
                            ClassName("dev.mattramotar.storex.mutablestore.core.api", "MutableStore")
                                .parameterizedBy(keyType, propertiesType, nodeType, errorType)
                        )
                    }

                }
                .build()
        )
        // Implement each operation by delegating to the private op properties
        .apply {

            if (OperationType.FindOne in operationTypes) {
                addProperty(
                    PropertySpec.builder(
                        "findOneOp",
                        ClassName(pkg, "FindOneOperationImpl")
                            .parameterizedBy(keyType, nodeType, errorType)
                    )
                        .initializer("FindOneOperationImpl(store, errorAdapter)")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )

                addFunction(
                    FunSpec.builder("findOne")
                        .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                        .addParameter("key", keyType)
                        .returns(
                            ClassName("dev.mattramotar.storex.result", "Result")
                                .parameterizedBy(nodeType, errorType)
                        )
                        .addCode("return findOneOp.findOne(key)")
                        .build()
                )
            }
            if (OperationType.FindOneComposite in operationTypes) {

                addProperty(
                    PropertySpec.builder(
                        "findOneCompositeOp",
                        ClassName(pkg, "FindOneCompositeOperationImpl")
                            .parameterizedBy(keyType, compositeType, errorType)
                    )
                        .initializer("FindOneCompositeOperationImpl(compositeStore, errorAdapter)")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )

                addFunction(
                    FunSpec.builder("findOneComposite")
                        .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                        .addParameter("key", keyType)
                        .returns(
                            ClassName("dev.mattramotar.storex.result", "Result")
                                .parameterizedBy(compositeType, errorType)
                        )
                        .addCode("return findOneCompositeOp.findOneComposite(key)")
                        .build()
                )
            }
            if (OperationType.UpdateOne in operationTypes) {

                addProperty(
                    PropertySpec.builder(
                        "updateOneOp",
                        ClassName(pkg, "UpdateOneOperationImpl")
                            .parameterizedBy(keyType, propertiesType, nodeType, errorType)
                    )
                        .initializer("UpdateOneOperationImpl(mutableStore, errorAdapter)")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )

                addFunction(
                    FunSpec.builder("updateOne")
                        .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                        .addParameter("key", keyType)
                        .addParameter("node", nodeType)
                        .returns(
                            ClassName("dev.mattramotar.storex.result", "Result")
                                .parameterizedBy(nodeType, errorType)
                        )
                        .addCode("return updateOneOp.updateOne(key, node)")
                        .build()
                )
            }
            if (OperationType.CreateOne in operationTypes) {
                addProperty(
                    PropertySpec.builder(
                        "createOneOp",
                        ClassName(pkg, "CreateOneOperationImpl")
                            .parameterizedBy(keyType, propertiesType, nodeType, errorType)
                    )
                        .initializer("CreateOneOperationImpl(mutableStore, errorAdapter)")
                        .addModifiers(KModifier.PRIVATE)
                        .build()
                )

                addFunction(
                    FunSpec.builder("createOne")
                        .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                        .addParameter("key", keyType)
                        .addParameter("properties", propertiesType)
                        .returns(
                            ClassName("dev.mattramotar.storex.result", "Result")
                                .parameterizedBy(nodeType, errorType)
                        )
                        .addCode("return createOneOp.createOne(key, properties)")
                        .build()
                )
            }
        }
        .build()
        .applyKspOrigins(originating)

    return FileSpec.builder(pkg, repoName)
        .addType(repoInterface)
        .addType(builderClass)
        .addType(generatedImpl)
        .build()
        .applyKspOrigins(originating)
}