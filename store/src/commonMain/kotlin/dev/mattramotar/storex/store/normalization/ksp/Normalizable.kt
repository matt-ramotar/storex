package dev.mattramotar.storex.store.normalization.ksp

//// module: storex-normalize-ksp-api
//package org.mobilenativefoundation.storex.normalize.ksp.api

import kotlin.annotation.AnnotationRetention.SOURCE
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.PROPERTY

/**
 * Marks a Kotlin class as a canonical entity that should get a generated EntityAdapter<T>.
 *
 * @param typeName Optional override for the canonical type name (default: class simpleName).
 */
@Target(CLASS)
@Retention(SOURCE)
annotation class Normalizable(val typeName: String = "")

/** Marks the single property that is this entity's stable identifier. */
@Target(PROPERTY)
@Retention(SOURCE)
annotation class EntityId

/**
 * Marks a property as a reference to another canonical entity (1:1 / many:1).
 * The property may be:
 *  - the entity type itself (recommended),
 *  - an EntityKey,
 *  - or the referenced id (String/Long/etc.).
 */
@Target(PROPERTY)
@Retention(SOURCE)
annotation class EntityRef(val typeName: String)

/**
 * Marks a property as a list of references (1:many / many:many).
 * The property may be:
 *  - List<ReferencedEntity>,
 *  - List<EntityKey>,
 *  - or List<String/Long/...> of referenced ids.
 */
@Target(PROPERTY)
@Retention(SOURCE)
annotation class EntityRefs(val typeName: String)

/**
 * Marks a property as embedded (not normalized into its own entity).
 * Embedded values are stored inline with the parent as a Scalar.
 * If you’re using kotlinx.serialization, prefer JsonString encoding.
 */
@Target(PROPERTY)
@Retention(SOURCE)
annotation class Embedded(
    val mode: EmbeddedMode = EmbeddedMode.JsonString,
    val columnName: String = "" // optional override (default: property name)
)

enum class EmbeddedMode { JsonString, InlineMap }

/** Overrides the field name used in the NormalizedRecord for this property. */
@Target(PROPERTY)
@Retention(SOURCE)
annotation class NormalizedName(val value: String)

/** Exclude a property from normalization entirely. */
@Target(PROPERTY)
@Retention(SOURCE)
annotation class IgnoreNormalized

/**
 * Defines the entry point anchor for the generated SchemaRegistry.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class NormalizationSchema