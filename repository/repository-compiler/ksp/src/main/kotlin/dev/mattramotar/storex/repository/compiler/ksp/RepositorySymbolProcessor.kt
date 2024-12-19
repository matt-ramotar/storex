@file:OptIn(KspExperimental::class)

package dev.mattramotar.storex.repository.compiler.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import dev.mattramotar.storex.repository.runtime.annotations.RepositoryConfig

class RepositorySymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val packageName: String?,
    private val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols =
            resolver.getSymbolsWithAnnotation(RepositoryConfig::class.qualifiedName!!).filterIsInstance<KSClassDeclaration>()
                .distinctBy { it.qualifiedName }
                .filter { it.validate() }
                .toList()

        for (symbol in symbols) {
            symbol.accept(RepositoryVisitor(codeGenerator, packageName, logger), Unit)
        }

        return emptyList()
    }
}

