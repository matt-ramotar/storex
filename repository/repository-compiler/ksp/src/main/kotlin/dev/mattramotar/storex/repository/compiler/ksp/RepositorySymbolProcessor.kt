package dev.mattramotar.storex.repository.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import dev.mattramotar.storex.repository.compiler.ksp.extensions.KSAnnotationExtensions.findEnumListArgument
import dev.mattramotar.storex.repository.compiler.ksp.extensions.KSAnnotationExtensions.findKSTypeArgument
import dev.mattramotar.storex.repository.compiler.ksp.extensions.KSAnnotationExtensions.findStringArgument
import dev.mattramotar.storex.repository.compiler.ksp.poet.createRepositoryInterfaceAndBuilder
import dev.mattramotar.storex.repository.compiler.ksp.poet.operations.mutation.createCreateOneFileSpec
import dev.mattramotar.storex.repository.compiler.ksp.poet.operations.mutation.createUpdateOneFileSpec
import dev.mattramotar.storex.repository.compiler.ksp.poet.operations.query.createFindOneCompositeFileSpec
import dev.mattramotar.storex.repository.compiler.ksp.poet.operations.query.createFindOneOperationFileSpec
import dev.mattramotar.storex.repository.runtime.OperationType
import dev.mattramotar.storex.repository.runtime.annotations.RepositoryConfig

class RepositorySymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val packageName: String?
) : SymbolProcessor {


    override fun process(resolver: Resolver): List<KSAnnotated> {
        // 1) Find all symbols annotated with @RepositoryConfig
        val symbols = resolver
            .getSymbolsWithAnnotation(RepositoryConfig::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        // For each annotated class/object, generate the repository code
        symbols.forEach { ksClass ->
            generateRepositoryFromConfig(ksClass)
        }

        // Return empty — no deferred symbols
        return emptyList()
    }

    /**
     * Reads annotation arguments and generates the corresponding source files.
     */
    private fun generateRepositoryFromConfig(ksClass: KSClassDeclaration) {
        val packageName = packageName ?: ksClass.packageName.asString()

        // Parse the @RepositoryConfig annotation
        val annotation: KSAnnotation = ksClass.annotations.firstOrNull {
            it.shortName.asString() == RepositoryConfig::class.simpleName
        } ?: return

        // Retrieve annotation arguments
        val nameArg = annotation.findStringArgument("name") ?: return
        val keyTypeArg = annotation.findKSTypeArgument("keyType") ?: return
        val nodeTypeArg = annotation.findKSTypeArgument("nodeType") ?: return
        val propertiesTypeArg = annotation.findKSTypeArgument("propertiesType")
        val compositeTypeArg = annotation.findKSTypeArgument("compositeType")
        val errorTypeArg = annotation.findKSTypeArgument("errorType")
        val opsArg = annotation.findEnumListArgument("operations", OperationType::class)

        // Convert to KotlinPoet TypeNames
        val keyTypeName = keyTypeArg.toTypeName()
        val nodeTypeName = nodeTypeArg.toTypeName()
        val partialTypeName = propertiesTypeArg?.toTypeName() ?: ANY
        val compositeTypeName = compositeTypeArg?.toTypeName() ?: ANY
        val errorTypeName = errorTypeArg?.toTypeName() ?: ClassName("kotlin", "Throwable")

        // We'll generate everything into this package:

        // Generate each requested operation
        if (OperationType.FindOne in opsArg) {
            createFindOneOperationFileSpec(
                packageName,
                ksClass.containingFile
            ).writeTo(codeGenerator, aggregating = false)
        }

        if (OperationType.FindOneComposite in opsArg) {
            createFindOneCompositeFileSpec(
                packageName,
                keyTypeName,
                ksClass.containingFile
            ).writeTo(codeGenerator, aggregating = false)
        }

        if (OperationType.UpdateOne in opsArg) {
            createUpdateOneFileSpec(
                packageName,
                ksClass.containingFile
            ).writeTo(codeGenerator, aggregating = false)
        }

        if (OperationType.CreateOne in opsArg) {
            createCreateOneFileSpec(
                packageName,
                ksClass.containingFile
            ).writeTo(codeGenerator, aggregating = false)
        }

        // Finally, create the repository interface + builder
        createRepositoryInterfaceAndBuilder(
            packageName,
            nameArg,
            opsArg,
            keyTypeName,
            nodeTypeName,
            partialTypeName,
            compositeTypeName,
            errorTypeName,
            ksClass.containingFile
        ).writeTo(codeGenerator, aggregating = false)
    }
}




