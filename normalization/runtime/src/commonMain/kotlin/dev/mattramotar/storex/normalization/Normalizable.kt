package dev.mattramotar.storex.normalization


//// module: storex-normalize-ksp-api
//package org.mobilenativefoundation.storex.normalize.ksp.api

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.PROPERTY

/**
 * Marks a Kotlin class as a canonical entity that should get a generated EntityAdapter<T>.
 *
 * @param typeName Optional override for the canonical type name (default: class simpleName).
 */
@Target(CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Normalizable(
    /** Optional override for the canonical entity type name. Default is derived from class name. */
    val typeName: String = ""
)

/** Marks the single property that is this entity's stable identifier. */
@Target(PROPERTY)
@Retention(BINARY)
annotation class EntityId

/**
 * Marks a property as a reference to another canonical entity (1:1 / many:1).
 * The property may be:
 *  - the entity type itself (recommended),
 *  - an EntityKey,
 *  - or the referenced id (String/Long/etc.).
 */
@Target(PROPERTY)
@Retention(BINARY)
annotation class EntityRef(val typeName: String)

/**
 * Marks a property as a list of references (1:many / many:many).
 * The property may be:
 *  - List<ReferencedEntity>,
 *  - List<EntityKey>,
 *  - or List<String/Long/...> of referenced ids.
 */
@Target(PROPERTY)
@Retention(BINARY)
annotation class EntityRefs(val typeName: String)

/**
 * Marks a property as embedded (not normalized into its own entity).
 * Embedded values are stored inline with the parent as a Scalar.
 * If you’re using kotlinx.serialization, prefer JsonString encoding.
 */
@Target(PROPERTY)
@Retention(BINARY)
annotation class Embedded(
    val mode: EmbeddedMode = EmbeddedMode.JsonString,
    val columnName: String = "" // optional override (default: property name)
)

enum class EmbeddedMode { JsonString, InlineMap }

/** Overrides the field name used in the NormalizedRecord for this property. */
@Target(PROPERTY)
@Retention(BINARY)
annotation class NormalizedName(val value: String)

/** Exclude a property from normalization entirely. */
@Target(PROPERTY)
@Retention(BINARY)
annotation class IgnoreNormalized

/**
 * Defines the entry point anchor for the generated SchemaRegistry.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NormalizationSchema

/** Whether to include nulls in the mask (PATCH semantics). */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class IncludeWhenNull

/** Optionally override typeName for implicit ref detection on a property. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class RefTypeName(val value: String)


@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class EntityRefId(val typeName: String)
