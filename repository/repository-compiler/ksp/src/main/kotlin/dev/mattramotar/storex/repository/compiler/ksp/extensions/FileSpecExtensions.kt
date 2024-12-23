package dev.mattramotar.storex.repository.compiler.ksp.extensions

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile

internal object FileSpecExtensions {
    /**
     * Attaches the [ksFile] as originating to each top-level declaration
     * (types, functions, properties, type aliases).
     */
    fun FileSpec.applyKspOrigins(ksFile: KSFile?): FileSpec {
        if (ksFile == null) return this

        // Rebuild the FileSpec via a new builder
        val newFileBuilder = FileSpec.builder(this.packageName, this.name)

        // Re-add each top-level type
        for (typeSpec in this.members.filterIsInstance<TypeSpec>()) {
            val newType = typeSpec.toBuilder()
                .addOriginatingKSFile(ksFile)
                .build()
            newFileBuilder.addType(newType)
        }

        // Re-add top-level functions
        for (funSpec in this.members.filterIsInstance<FunSpec>()) {
            val newFun = funSpec.toBuilder()
                .addOriginatingKSFile(ksFile)
                .build()
            newFileBuilder.addFunction(newFun)
        }

        // Re-add top-level properties
        for (propSpec in this.members.filterIsInstance<PropertySpec>()) {
            val newProp = propSpec.toBuilder()
                .addOriginatingKSFile(ksFile)
                .build()
            newFileBuilder.addProperty(newProp)
        }

        // Re-add top-level type aliases
        for (aliasSpec in this.members.filterIsInstance<TypeAliasSpec>()) {
            val newAlias = aliasSpec.toBuilder()
                .addOriginatingKSFile(ksFile)
                .build()
            newFileBuilder.addTypeAlias(newAlias)
        }

        // Finally build a new FileSpec
        return newFileBuilder.build()
    }

}