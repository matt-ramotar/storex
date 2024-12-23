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
import dev.mattramotar.storex.repository.compiler.ksp.poet.*
import dev.mattramotar.storex.repository.compiler.ksp.poet.operations.mutation.*
import dev.mattramotar.storex.repository.compiler.ksp.poet.operations.query.*
import dev.mattramotar.storex.repository.runtime.OperationType
import dev.mattramotar.storex.repository.runtime.annotations.RepositoryConfig

/**
 * Processes classes annotated with [RepositoryConfig], generating repository interfaces
 * and operations (like [createCreateOneFileSpec], [createUpdateOneFileSpec]).
 */
class RepositorySymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val packageName: String?
) : SymbolProcessor {

    /**
     * Main entry point for KSP. Scans for annotated classes and generates code.
     *
     * @return a list of any deferred symbols that couldn't be processed (usually empty).
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Find all symbols annotated with @RepositoryConfig
        val symbols = resolver
            .getSymbolsWithAnnotation(RepositoryConfig::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        // Generate the repository code for each annotated class
        symbols.forEach { ksClass ->
            generateRepositoryFromConfig(ksClass)
        }

        // No deferred symbols in typical usage
        return emptyList()
    }

    /**
     * Examines a single annotated class, extracts config details, and generates source files.
     */
    private fun generateRepositoryFromConfig(ksClass: KSClassDeclaration) {
        val resolvedPackageName = packageName ?: ksClass.packageName.asString()

        // The @RepositoryConfig annotation, if present.
        val annotation: KSAnnotation = ksClass.annotations.firstOrNull {
            it.shortName.asString() == RepositoryConfig::class.simpleName
        } ?: return

        // Parse annotation arguments
        val nameArg = annotation.findStringArgument("name") ?: return
        val keyTypeArg = annotation.findKSTypeArgument("keyType") ?: return
        val nodeTypeArg = annotation.findKSTypeArgument("nodeType") ?: return
        val propertiesTypeArg = annotation.findKSTypeArgument("propertiesType")
        val compositeTypeArg = annotation.findKSTypeArgument("compositeType")
        val errorTypeArg = annotation.findKSTypeArgument("errorType")
        val opsArg = annotation.findEnumListArgument("operations", OperationType::class)

        // Convert them to KotlinPoet TypeName
        val keyTypeName = keyTypeArg.toTypeName()
        val nodeTypeName = nodeTypeArg.toTypeName()
        val partialTypeName = propertiesTypeArg?.toTypeName() ?: ANY
        val compositeTypeName = compositeTypeArg?.toTypeName() ?: ANY
        val errorTypeName = errorTypeArg?.toTypeName() ?: ClassName("kotlin", "Throwable")

        // Generate code for each requested operation
        if (OperationType.FindOne in opsArg) {
            createFindOneOperationFileSpec(
                resolvedPackageName,
                ksClass.containingFile
            ).writeTo(codeGenerator, aggregating = false)
        }
        if (OperationType.FindOneComposite in opsArg) {
            createFindOneCompositeFileSpec(
                resolvedPackageName,
                keyTypeName,
                ksClass.containingFile
            ).writeTo(codeGenerator, aggregating = false)
        }
        if (OperationType.UpdateOne in opsArg) {
            createUpdateOneFileSpec(
                resolvedPackageName,
                ksClass.containingFile
            ).writeTo(codeGenerator, aggregating = false)
        }
        if (OperationType.CreateOne in opsArg) {
            createCreateOneFileSpec(
                resolvedPackageName,
                ksClass.containingFile
            ).writeTo(codeGenerator, aggregating = false)
        }

        // Finally, create the repository interface and builder
        createRepositoryInterfaceAndBuilder(
            resolvedPackageName,
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