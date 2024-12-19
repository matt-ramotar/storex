package dev.mattramotar.storex.repository.compiler.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class RepositorySymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return RepositorySymbolProcessor(
            codeGenerator = environment.codeGenerator,
            packageName = environment.options["repositoryPackageName"],
            logger = environment.logger
        )
    }
}