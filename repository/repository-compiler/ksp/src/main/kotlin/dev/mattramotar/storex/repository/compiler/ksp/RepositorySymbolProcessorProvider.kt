package dev.mattramotar.storex.repository.compiler.ksp


import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Entry point for KSP to instantiate [RepositorySymbolProcessor].
 */
class RepositorySymbolProcessorProvider : SymbolProcessorProvider {
    /**
     * Creates the KSP symbol processor.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RepositorySymbolProcessor(
            codeGenerator = environment.codeGenerator,
            packageName = environment.options["repositoryPackageName"]
        )
    }
}
