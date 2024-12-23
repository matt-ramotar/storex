package dev.mattramotar.storex.repository.compiler.ksp.extensions

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile

internal object TypeSpecExtensions {
    fun TypeSpec.applyKspOrigins(ksFile: KSFile?): TypeSpec {
        return if (ksFile != null) {
            this.toBuilder().addOriginatingKSFile(ksFile).build()
        } else this
    }
}