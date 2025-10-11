@file:OptIn(KspExperimental::class)

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import dev.mattramotar.storex.normalization.Embedded
import dev.mattramotar.storex.normalization.EmbeddedMode
import dev.mattramotar.storex.normalization.EntityId
import dev.mattramotar.storex.normalization.EntityRef
import dev.mattramotar.storex.normalization.EntityRefs
import dev.mattramotar.storex.normalization.IgnoreNormalized
import dev.mattramotar.storex.normalization.IncludeWhenNull
import dev.mattramotar.storex.normalization.Normalizable
import dev.mattramotar.storex.normalization.NormalizedName


data class DeriveConfig(
    val stripSuffixes: List<String> = emptyList(),
    val case: Case = Case.AsIs
) {
    enum class Case { AsIs, Pascal, UpperFirst }

    fun apply(name: String): String {
        var n = name
        stripSuffixes.forEach { s -> if (n.endsWith(s)) n = n.removeSuffix(s) }
        return when (case) {
            Case.AsIs -> n
            Case.Pascal, Case.UpperFirst -> n.replaceFirstChar { it.uppercase() }
        }
    }
}

/** Returns override if provided, else derives from class simple name with config applied. */
fun KSClassDeclaration.derivedTypeName(config: DeriveConfig): String {
    val ann = annotations.firstOrNull { it.shortName.asString() == Normalizable::class.simpleName }
    val override = ann?.arguments?.firstOrNull { it.name?.asString() == "typeName" }?.value as? String
    if (!override.isNullOrBlank()) return override
    return config.apply(simpleName.asString())
}

class NormalizationProcessor(private val env: SymbolProcessorEnvironment) : SymbolProcessor {

    private val codegen = env.codeGenerator
    private val logger = env.logger
    private val pkgGenerated = env.options["storex.normalization.package"] ?: "dev.mattramotar.storex.normalization.generated"

    private val deriveCfg = DeriveConfig(
        stripSuffixes = env.options["storex.normalization.stripSuffixes"]?.split(',')
            ?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        case = when (env.options["storex.normalization.case"]?.lowercase()) {
            "upperfirst", "pascal" -> DeriveConfig.Case.UpperFirst
            else -> DeriveConfig.Case.AsIs
        }
    )

    private val entityAdapters = mutableListOf<Pair<String, ClassName>>() // (typeName, adapterClass)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = resolver.getSymbolsWithAnnotation(Normalizable::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        annotated.forEach { decl ->
            try {
                generateAdapterFor(decl)
            } catch (t: Throwable) {
                logger.error("StoreX-Normalize KSP error: ${t.message}", decl)
            }
        }

        if (annotated.isNotEmpty()) {
            generateRegistry()
        }
        return emptyList()
    }

    private fun generateAdapterFor(decl: KSClassDeclaration) {
        if (!decl.isDataClass()) {
            logger.error("@Normalizable must be a data class", decl); return
        }
        if (!decl.isPublic()) {
            logger.error("@Normalizable class must be public", decl); return
        }

        val canonicalTypeName = decl.derivedTypeName(deriveCfg)

        val ksType = decl.asStarProjectedType()
        val className = decl.toClassName()
        val adapterName = "${className.simpleName}_EntityAdapter"
        val adapterClass = ClassName(pkgGenerated, adapterName)

        val idProp = decl.getAllProperties().firstOrNull { it.getAnnotationsByType(EntityId::class).any() }
            ?: run { logger.error("Missing @EntityId on ${decl.simpleName.asString()}", decl); return }

        val propSpecs = decl.getAllProperties()
            .filterNot { it.getAnnotationsByType(IgnoreNormalized::class).any() }
            .toList()

        // Build normalize() body
        val normalizeFun = FunSpec.Companion.builder("normalize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("entity", className)
            .addParameter("ctx", NormalizationContextClass)
            .returns(PairNormalizedRecordMask)
            .addCode(buildNormalizeBody(propSpecs, idProp))

        // Build denormalize() body
        val denormFun = FunSpec.Companion.builder("denormalize")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("record", NormalizedRecordAlias)
            .addParameter("ctx", DenormalizationContextClass)
            .returns(className)
            .addCode(buildDenormalizeBody(className, propSpecs, idProp))

        val extractIdFun = FunSpec.Companion.builder("extractId")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("entity", className)
            .returns(String::class)
            .addStatement("return entity.%L.toString()", idProp.simpleName.getShortName())

        val typeNameProp = PropertySpec.Companion.builder("typeName", String::class)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("%S", canonicalTypeName)
            .build()

        val adapterType = TypeSpec.Companion.objectBuilder(adapterName)
            .addSuperinterface(EntityAdapterClass.parameterizedBy(className))
            .addProperty(typeNameProp)
            .addFunction(extractIdFun.build())
            .addFunction(normalizeFun.build())
            .addFunction(denormFun.build())
            .build()

        FileSpec.Companion.builder(pkgGenerated, adapterName)
            .addFileComment("Generated by StoreX-Normalize KSP. Do not edit.")
            .addType(adapterType)
            .build()
            .writeTo(codegen, aggregating = true)

        entityAdapters += canonicalTypeName to adapterClass
    }

    private fun generateRegistry() {

        val byName = entityAdapters.groupBy { it.first }
        val collisions = byName.filterValues { it.size > 1 }.keys
        if (collisions.isNotEmpty()) {
            // You can choose warn or error. I recommend: error if the adapters come from different classes.
            collisions.forEach { tn ->
                logger.error(
                    "StoreX-Normalize: Duplicate typeName '$tn'. " +
                        "Use @Normalizable(\"…\") override or KSP arg 'storex.normalization.stripSuffixes' to avoid collisions."
                )
            }
            return
        }

        val mapEntries = CodeBlock.Companion.builder()
        mapEntries.add("mapOf(\n")
        entityAdapters.forEachIndexed { idx, (typeName, adapterClass) ->
            mapEntries.add("  %S to %T", typeName, adapterClass)
            if (idx != entityAdapters.lastIndex) mapEntries.add(",\n") else mapEntries.add("\n")
        }
        mapEntries.add(")")

        val registryType = TypeSpec.Companion.classBuilder("SchemaRegistry_Generated")
            .superclass(SchemaRegistryClass)
            .primaryConstructor(FunSpec.Companion.constructorBuilder().build())
            .addSuperclassConstructorParameter(mapEntries.build())
            .build()

        FileSpec.Companion.builder(pkgGenerated, "SchemaRegistry_Generated")
            .addFileComment("Generated by StoreX-Normalize KSP. Do not edit.")
            .addType(registryType)
            .build()
            .writeTo(codegen, aggregating = true)
    }

    // === code gen helpers ===

    private fun buildNormalizeBody(
        props: List<KSPropertyDeclaration>,
        idProp: KSPropertyDeclaration
    ): CodeBlock {
        val b = CodeBlock.Companion.builder()
        b.addStatement("val rec = linkedMapOf<String, %T>()", NormalizedValueClass)
        b.addStatement("val mask = linkedSetOf<String>()")

        props.forEach { prop ->

            // pseudo where `prop` is KSPropertyDeclaration, `b` is CodeBlock.Builder
            when (val k = prop.classify()) {
                is PropKind.Id -> {
                    b.addStatement("rec[%S] = %T(entity.%L)", k.fieldName, ScalarCtor, prop.simpleName.asString())
                    b.addStatement("mask += %S", k.fieldName)
                }
                is PropKind.Embedded -> {
                    val col = k.columnName ?: k.fieldName
                    b.addStatement("val json = %T.encodeToString(entity.%L)", JsonClass, prop.simpleName.asString())
                    b.addStatement("rec[%S] = %T(json)", col, ScalarCtor)
                    b.addStatement("mask += %S", col)
                }
                is PropKind.Ref -> {
                    if (k.typeName == "_fromKey_") {
                        // property is EntityKey; just store it
                        b.addStatement("rec[%S] = %T(entity.%L)", k.fieldName, RefCtor, prop.simpleName.asString())
                    } else {
                        b.addStatement("val key = ctx.registerNested(entity.%L)", prop.simpleName.asString())
                        b.addStatement("rec[%S] = %T(key)", k.fieldName, RefCtor)
                    }
                    b.addStatement("mask += %S", k.fieldName)
                }
                is PropKind.RefList -> {
                    if (k.typeName == "_fromKey_") {
                        b.addStatement("rec[%S] = %T(entity.%L)", k.fieldName, RefListCtor, prop.simpleName.asString())
                    } else {
                        b.addStatement("val keys = (entity.%L ?: emptyList()).map { ctx.registerNested(it) }", prop.simpleName.asString())
                        b.addStatement("rec[%S] = %T(keys)", k.fieldName, RefListCtor)
                    }
                    b.addStatement("mask += %S", k.fieldName)
                }
                is PropKind.Scalar -> {
                    if (k.includeWhenNull) {
                        b.addStatement("rec[%S] = %T(entity.%L)", k.fieldName, ScalarCtor, prop.simpleName.asString())
                        b.addStatement("mask += %S", k.fieldName)
                    } else {
                        b.beginControlFlow("if (entity.%L != null)", prop.simpleName.asString())
                        b.addStatement("rec[%S] = %T(entity.%L)", k.fieldName, ScalarCtor, prop.simpleName.asString())
                        b.addStatement("mask += %S", k.fieldName)
                        b.endControlFlow()
                    }
                }
                is PropKind.ScalarList -> {
                    b.beginControlFlow("if (entity.%L != null)", prop.simpleName.asString())
                    b.addStatement("rec[%S] = %T(entity.%L)", k.fieldName, ScalarListCtor, prop.simpleName.asString())
                    b.addStatement("mask += %S", k.fieldName)
                    b.endControlFlow()
                }
            }
        }

        b.addStatement("return rec to mask")
        return b.build()
    }

    private fun buildDenormalizeBody(
        className: ClassName,
        props: List<KSPropertyDeclaration>,
        idProp: KSPropertyDeclaration
    ): CodeBlock {
        val b = CodeBlock.Companion.builder()
        val ctorArgs = mutableListOf<String>()

        props.forEach { p ->
            val name = p.simpleName.asString()
            val fieldName = p.getAnnotationsByType(NormalizedName::class).firstOrNull()?.value ?: name

            val ref = p.getAnnotationsByType(EntityRef::class).firstOrNull()
            val refs = p.getAnnotationsByType(EntityRefs::class).firstOrNull()
            val emb = p.getAnnotationsByType(Embedded::class).firstOrNull()

            when {
                ref != null -> {
                    b.addStatement("val %LRef = record[%S] as? %T", name, fieldName, RefType)
                    val typeName = p.type.toTypeName()
                    if (typeName.isNullable) {
                        b.addStatement(
                            "val %L = %LRef?.let { ctx.resolveReference(it.key) } as? %T",
                            name,
                            name,
                            typeName
                        )
                    } else {
                        b.addStatement(
                            "val %L = (%LRef?.let { ctx.resolveReference(it.key) } as? %T) ?: error(%S)",
                            name,
                            name,
                            typeName,
                            "Missing required reference: $name"
                        )
                    }
                    ctorArgs += name
                }

                refs != null -> {
                    b.addStatement("val %LRefs = record[%S] as? %T", name, fieldName, RefListType)
                    val listTypeName = p.type.toTypeName()
                    val elementTypeName = p.type.resolveListArgTypeName()
                    if (listTypeName.isNullable) {
                        b.addStatement(
                            "val %L = %LRefs?.keys?.mapNotNull { ctx.resolveReference(it) as? %T }",
                            name,
                            name,
                            elementTypeName
                        )
                    } else {
                        b.addStatement(
                            "val %L = %LRefs?.keys?.mapNotNull { ctx.resolveReference(it) as? %T } ?: emptyList()",
                            name,
                            name,
                            elementTypeName
                        )
                    }
                    ctorArgs += name
                }

                emb != null -> {
                    val column = if (emb.columnName.isNotBlank()) emb.columnName else fieldName
                    b.addStatement("val %LJson = (record[%S] as? %T)?.value as? String", name, column, ScalarType)
                    b.addStatement(
                        "val %L = %LJson?.let { %T.decodeFromString<%T>(it) }",
                        name,
                        name,
                        JsonClass,
                        p.type.toTypeName().copy(nullable = false)
                    )
                    ctorArgs += name
                }

                else -> {
                    if (p.isListLike()) {
                        b.addStatement("val %LVal = record[%S] as? %T", name, fieldName, ScalarListType)
                        b.addStatement(
                            "val %L = %LVal?.values?.map { it as %T } ?: emptyList()",
                            name,
                            name,
                            p.type.resolveListArgTypeName()
                        )
                    } else {
                        b.addStatement("val %LVal = record[%S] as? %T", name, fieldName, ScalarType)
                        b.addStatement("val %L = %LVal?.value as %T", name, name, p.type.toTypeName())
                    }
                    ctorArgs += name
                }
            }
        }

        b.addStatement("return %T(%L)", className, ctorArgs.joinToString(", "))
        return b.build()
    }

    // === tiny helpers (you can move to a Util) ===
    fun KSClassDeclaration.isDataClass(): Boolean =
        classKind == ClassKind.CLASS && Modifier.DATA in modifiers

    /** True if this property’s declared type is a Kotlin List/Set/Array. */
    fun KSPropertyDeclaration.isListLike(): Boolean {
        val t = type.resolve()
        val qn = t.declaration.qualifiedName?.asString() ?: return false
        return qn == "kotlin.collections.List" ||
            qn == "kotlin.collections.Set"  ||
            qn == "kotlin.Array"
    }

    /** Element type (T) for List<T>/Set<T>/Array<T>; falls back to `Any?` if unknown. */
    fun KSPropertyDeclaration.listElementKSType(): KSType? {
        val t = type.resolve()
        val qn = t.declaration.qualifiedName?.asString()
        return when (qn) {
            "kotlin.collections.List", "kotlin.collections.Set" ->
                t.arguments.firstOrNull()?.type?.resolve()
            "kotlin.Array" ->
                t.arguments.firstOrNull()?.type?.resolve()
            else -> null
        }
    }

    /** The TypeName for List/Set/Array element; useful in codegen. */
    fun KSPropertyDeclaration.listElementTypeNameOrAny(): TypeName =
        (listElementKSType()?.toClassName() ?: ClassName("kotlin", "Any")).copy(nullable = true)

    /** True if a type is the StoreX EntityKey. */
    fun KSType.isEntityKey(): Boolean =
        (declaration.qualifiedName?.asString() == "dev.mattramotar.storex.normalization.keys.EntityKey")

    /** True if the underlying type (class/alias) is annotated with @Normalizable. */
    fun KSType.isAnnotatedNormalizable(): Boolean =
        (declaration as? KSClassDeclaration)
            ?.getAnnotationsByType(Normalizable::class)
            ?.any() == true

    /** If annotated, compute the canonical typename using the same derivation path. */
    fun KSType.normalizableTypeNameOrNull(derive: DeriveConfig): String? {
        val decl = declaration as? KSClassDeclaration ?: return null
        return if (decl.getAnnotationsByType(Normalizable::class).any()) decl.derivedTypeName(derive) else null
    }

    private fun KSTypeReference.resolveListArgTypeName(): TypeName {
        val ksType = this.resolve()
        val arg = ksType.arguments.firstOrNull()?.type?.resolve() ?: return ANY
        return arg.toTypeName()
    }

    /** Field/column name mapping helper. */
    fun KSPropertyDeclaration.normalizedFieldName(): String =
        this.getAnnotationsByType(NormalizedName::class).firstOrNull()?.value
            ?: this.simpleName.asString()

    /** Classify a property into one of the normalization kinds with implicit ref rules. */
    fun KSPropertyDeclaration.classify(): PropKind {
        val field = normalizedFieldName()

        // Highest precedence: explicit annotations
        if (getAnnotationsByType(EntityId::class).any()) {
            return PropKind.Id(field)
        }
        getAnnotationsByType(Embedded::class).firstOrNull()?.let { emb ->
            return PropKind.Embedded(field, emb.mode, emb.columnName.takeIf { it.isNotBlank() })
        }
        getAnnotationsByType(EntityRef::class).firstOrNull()?.let { ref ->
            return PropKind.Ref(field, ref.typeName)
        }
        getAnnotationsByType(EntityRefs::class).firstOrNull()?.let { refs ->
            return PropKind.RefList(field, refs.typeName)
        }

        // Implicit references
        val t: KSType = this.type.resolve()

        // If property type is EntityKey, it's a reference (no nested normalize)
        if (t.isEntityKey()) {
            // typeName comes from EntityKey at runtime; we still treat it as Ref
            // For codegen, use a sentinel like "_fromKey_" if you need branching
            return PropKind.Ref(field, typeName = "_fromKey_")
        }

        // If property is normalizable, implicit Ref
        t.normalizableTypeNameOrNull(deriveCfg)?.let { tn ->
            return PropKind.Ref(field, tn)
        }

        // Lists/Sets/Arrays
        if (isListLike()) {
            listElementKSType()?.let { elem ->
                if (elem.isEntityKey()) return PropKind.RefList(field, "_fromKey_")
                elem.normalizableTypeNameOrNull(deriveCfg)?.let { tn -> return PropKind.RefList(field, tn) }
            }
            return PropKind.ScalarList(field)
        }

        // Everything else is a scalar. Allow opt-in to include nulls in mask.
        val includeNull = getAnnotationsByType(IncludeWhenNull::class).any()
        return PropKind.Scalar(field, includeNull)
    }


    private fun emitResolveRefFromProperty(expr: String, typeName: String): CodeBlock {
        // entity instance → registerNested, else EntityKey, else id string
        return CodeBlock.Companion.of(
            "when (val v = %L) { " +
                "is %T -> ctx.registerNested(v); " +
                "is %T -> v; " +
                "else -> %T(%S, v.toString()) " +
                "}",
            expr,
            ANY, // fallback, caller codegen will ensure the first case matches the referenced class
            EntityKeyClass,
            EntityKeyClass, typeName
        )
    }

    private fun emitResolveRefsFromProperty(expr: String, typeName: String): CodeBlock {
        return CodeBlock.Companion.of(
            "(%L as kotlin.collections.Collection<*>).map { e -> " +
                "when (e) { " +
                "is %T -> ctx.registerNested(e); " +
                "is %T -> e; " +
                "else -> %T(%S, e.toString()) " +
                "} " +
                "}",
            expr,
            ANY,
            EntityKeyClass,
            EntityKeyClass, typeName
        )
    }

    // === referenced types (Poet shortcuts) ===

    private val EntityAdapterClass = ClassName("dev.mattramotar.storex.normalization.schema", "EntityAdapter")
    private val SchemaRegistryClass = ClassName("dev.mattramotar.storex.normalization.schema", "SchemaRegistry")
    private val NormalizedRecordAlias = ClassName("dev.mattramotar.storex.normalization.format", "NormalizedRecord")
    private val NormalizedValueClass = ClassName("dev.mattramotar.storex.normalization.format", "NormalizedValue")
    private val ScalarCtor = NormalizedValueClass.nestedClass("Scalar")
    private val ScalarListCtor = NormalizedValueClass.nestedClass("ScalarList")
    private val RefCtor = NormalizedValueClass.nestedClass("Ref")
    private val RefListCtor = NormalizedValueClass.nestedClass("RefList")
    private val ScalarType = ScalarCtor
    private val ScalarListType = ScalarListCtor
    private val RefType = RefCtor
    private val RefListType = RefListCtor
    private val EntityKeyClass = ClassName("dev.mattramotar.storex.normalization.keys", "EntityKey")
    private val NormalizationContextClass = ClassName("dev.mattramotar.storex.normalization.schema", "NormalizationContext")
    private val DenormalizationContextClass = ClassName("dev.mattramotar.storex.normalization.schema", "DenormalizationContext")
    private val PairNormalizedRecordMask = ClassName("kotlin", "Pair")
        .parameterizedBy(NormalizedRecordAlias, ClassName("kotlin.collections", "Set").parameterizedBy(String::class.asClassName()))
    private val JsonClass = ClassName("kotlinx.serialization.json", "Json")


}


sealed class PropKind {
    data class Id(val fieldName: String) : PropKind()
    data class Embedded(val fieldName: String, val mode: EmbeddedMode, val columnName: String?) : PropKind()
    data class Ref(val fieldName: String, val typeName: String) : PropKind()
    data class RefList(val fieldName: String, val typeName: String) : PropKind()
    data class Scalar(val fieldName: String, val includeWhenNull: Boolean) : PropKind()
    data class ScalarList(val fieldName: String) : PropKind()
}






// Sample
@Normalizable
data class User(
    @EntityId val id: String,
    val displayName: String,
)

@Normalizable
data class Comment(
    @EntityId val id: String,
    val text: String,
    val user: User
)





/**
 *
 *  That’s it for defaults. If you never pass any KSP args, a class named UserDto will default to "UserDto". If you set:
 *
 * ksp {
 *   arg("storex.normalization.stripSuffixes", "Dto,Entity,Model")
 *   arg("storex.normalization.case", "upperFirst")
 * }
 *
 *
 * then UserDto → "User", postEntity → "PostEntity" (then UpperFirst → "PostEntity"—unchanged), etc.
 *
 *
 * 5) Multi‑module & cross‑module implicit refs
 *
 * With BINARY retention on @Normalizable, KSP in downstream modules can see that a dependency’s class is normalizable, so implicit refs work across modules.
 *
 * If you rely on suffix stripping (stripSuffixes) for derivation, use the same KSP args in all modules to avoid mismatches.
 *
 * When a property references a normalizable class from a library that doesn’t apply KSP (and thus doesn’t generate an adapter), you still need an adapter from somewhere. Either:
 *
 * add @EntityRef("TypeName") explicitly on the property to pin the typename, and generate an adapter for that type in your app; or
 *
 * put @Normalizable on the library class and include the KSP processor on the library.
 *
 *
 * End‑to‑end mental model after this change
 *
 * @Normalizable without an argument gives you a typeName equal to the class name post derivation (strip suffixes, case tweak).
 *
 * @Normalizable("Custom") overrides it explicitly.
 *
 * Property refs are implicit if the property (or list element) type is @Normalizable—the typename is the same derived one.
 *
 * Everything funnels into a single canonical typename string that keys your normalized graph (EntityKey(typeName, id)), your adapters, and your schema registry.
 *
 *
 */