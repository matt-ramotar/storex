package dev.mattramotar.storex.store.mutation

import dev.mattramotar.storex.store.StoreKey

/**
 * Simplified mutation encoder for the common case where all network mutation types use the same DTO.
 *
 * Reduces [MutationEncoder]'s 6 generic parameters to 4 by collapsing:
 * - `NetworkPatch`, `NetworkDraft`, and `NetworkPut` → `Network` (single unified network DTO)
 *
 * ## Rationale
 *
 * The full [MutationEncoder] interface has 6 generic parameters:
 * ```
 * MutationEncoder<Patch, Draft, Domain, NetworkPatch, NetworkDraft, NetworkPut>
 * ```
 *
 * In practice, most APIs use the same network representation for all mutation types:
 * - Patches, drafts, and full values all serialize to the same DTO
 * - The server determines the operation type from HTTP method + endpoint, not payload type
 *
 * [SimpleMutationEncoder] collapses NetworkPatch/NetworkDraft/NetworkPut into a single Network type:
 * ```
 * SimpleMutationEncoder<Patch, Draft, Domain, Network>
 * ```
 *
 * ## Example: Identity Encoding (No Transformation)
 * ```kotlin
 * // When your domain types are already serializable
 * class NoOpEncoder<P, D, V> : SimpleMutationEncoder<P, D, V, V> {
 *     override suspend fun encodePatch(patch: P, base: V?): V? = patch as? V
 *     override suspend fun encodeDraft(draft: D): V? = draft as? V
 *     override suspend fun encodeValue(value: V): V = value
 * }
 * ```
 *
 * ## Example: DTO Conversion
 * ```kotlin
 * // When you need to convert domain types to API DTOs
 * class UserMutationEncoder : SimpleMutationEncoder<UserPatch, UserDraft, User, UserDto> {
 *     override suspend fun encodePatch(patch: UserPatch, base: User?): UserDto? {
 *         return UserDto(
 *             name = patch.name,          // Only fields in patch
 *             email = patch.email,
 *             // Leave other fields null (sparse update)
 *         )
 *     }
 *
 *     override suspend fun encodeDraft(draft: UserDraft): UserDto? {
 *         return UserDto(
 *             name = draft.name,          // All required fields for creation
 *             email = draft.email,
 *             role = draft.initialRole,
 *         )
 *     }
 *
 *     override suspend fun encodeValue(value: User): UserDto {
 *         return UserDto(
 *             id = value.id,
 *             name = value.name,          // All fields for full replacement
 *             email = value.email,
 *             role = value.role,
 *         )
 *     }
 * }
 * ```
 *
 * ## Example: Optimistic Update Support
 * ```kotlin
 * // When you need to apply patches locally before server confirmation
 * class ArticleEncoder : SimpleMutationEncoder<ArticlePatch, ArticleDraft, Article, ArticleJson> {
 *     override suspend fun encodePatch(patch: ArticlePatch, base: Article?): ArticleJson? {
 *         // Convert patch to JSON
 *         return ArticleJson(
 *             title = patch.title,
 *             content = patch.content,
 *         )
 *     }
 *
 *     override suspend fun encodeDraft(draft: ArticleDraft): ArticleJson? {
 *         return ArticleJson(
 *             title = draft.title,
 *             content = draft.initialContent,
 *             authorId = draft.authorId,
 *         )
 *     }
 *
 *     override suspend fun encodeValue(value: Article): ArticleJson {
 *         return ArticleJson(
 *             id = value.id,
 *             title = value.title,
 *             content = value.content,
 *             authorId = value.author.id,
 *         )
 *     }
 *
 *     override suspend fun applyPatchLocally(base: Article, patch: ArticlePatch): Article {
 *         // Enable optimistic updates
 *         return base.copy(
 *             title = patch.title ?: base.title,
 *             content = patch.content ?: base.content,
 *         )
 *     }
 * }
 * ```
 *
 * @param Patch The type for partial updates (PATCH operations)
 * @param Draft The type for resource creation (POST operations)
 * @param Domain The application's domain model type
 * @param Network The unified network/DTO type for all mutation operations
 *
 * @see MutationEncoder For full 6-parameter version with separate NetworkPatch/NetworkDraft/NetworkPut
 * @see IdentityMutationEncoder For when no encoding is needed
 */
interface SimpleMutationEncoder<Patch, Draft, Domain, Network> {

    /**
     * Encodes a patch for network transmission.
     *
     * Called when performing update operations (PATCH/POST to existing resource).
     *
     * @param patch The patch to encode
     * @param base The current value (if available) for context
     * @return The network representation, or null if encoding fails
     */
    suspend fun encodePatch(patch: Patch, base: Domain?): Network?

    /**
     * Encodes a draft for resource creation.
     *
     * Called when creating new resources (POST operations).
     *
     * @param draft The draft to encode
     * @return The network representation, or null if encoding fails
     */
    suspend fun encodeDraft(draft: Draft): Network?

    /**
     * Encodes a complete value for upsert/replace operations.
     *
     * Called when performing PUT operations (upsert, replace).
     *
     * @param value The complete domain value
     * @return The network representation
     */
    suspend fun encodeValue(value: Domain): Network

    /**
     * Optionally applies a patch to a base value for optimistic updates.
     *
     * If implemented, allows the store to immediately update local state
     * before the server confirms the operation. Return null to disable
     * optimistic updates.
     *
     * @param base The current value
     * @param patch The patch to apply
     * @return The optimistically updated value, or null to disable optimistic updates
     */
    suspend fun applyPatchLocally(base: Domain, patch: Patch): Domain? = null
}

/**
 * Identity encoder that passes values through unchanged.
 *
 * Use when:
 * - Patch, Draft, and Domain are all the same type
 * - No encoding/transformation needed
 * - Your domain types are directly serializable
 *
 * @param Domain The application's domain model type (used for all mutation parameters)
 *
 * ## Example
 * ```kotlin
 * val encoder = IdentityMutationEncoder<UserDto>()
 * val dto = encoder.encodePatch(userDto, null)  // Returns userDto unchanged
 * ```
 */
class IdentityMutationEncoder<Domain> : SimpleMutationEncoder<Domain, Domain, Domain, Domain> {
    override suspend fun encodePatch(patch: Domain, base: Domain?): Domain = patch
    override suspend fun encodeDraft(draft: Domain): Domain = draft
    override suspend fun encodeValue(value: Domain): Domain = value
}

/**
 * Creates an identity mutation encoder that passes values through unchanged.
 *
 * Convenient factory function for when your Patch, Draft, and Domain types are all
 * the same and require no transformation for network transmission.
 *
 * @param Domain The application's domain model type
 * @return An identity encoder that returns inputs unchanged
 *
 * ## Example
 * ```kotlin
 * val store = mutationStore<UserKey, UserDto, UserDto, UserDto> {
 *     encoder = identityMutationEncoder()
 * }
 * ```
 */
fun <Domain> identityMutationEncoder(): SimpleMutationEncoder<Domain, Domain, Domain, Domain> = IdentityMutationEncoder()

/**
 * Adapter to use a [SimpleMutationEncoder] as a [MutationEncoder] (for backward compatibility).
 *
 * Expands [SimpleMutationEncoder]'s 4 parameters to [MutationEncoder]'s 6 parameters by:
 * - Setting NetworkPatch = Network
 * - Setting NetworkDraft = Network
 * - Setting NetworkPut = Network
 *
 * This allows simplified encoders to be used in contexts requiring the full [MutationEncoder] interface.
 *
 * @param Patch The type for partial updates (PATCH operations)
 * @param Draft The type for resource creation (POST operations)
 * @param Domain The application's domain model type
 * @param Network The unified network/DTO type for all mutation operations
 */
internal class SimpleMutationEncoderAdapter<Patch, Draft, Domain, Network>(
    private val simple: SimpleMutationEncoder<Patch, Draft, Domain, Network>
) : MutationEncoder<Patch, Draft, Domain, Network, Network, Network> {

    override suspend fun fromPatch(patch: Patch, base: Domain?): Network? {
        return simple.encodePatch(patch, base)
    }

    override suspend fun fromDraft(draft: Draft): Network? {
        return simple.encodeDraft(draft)
    }

    override suspend fun fromValue(value: Domain): Network? {
        return simple.encodeValue(value)
    }
}
