package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.StoreKey
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Inputs to decide whether a fetch is necessary and whether it can be conditional.
 */
data class FreshnessContext<K : StoreKey, DbMeta>(
    val key: K,
    val now: Instant,
    val freshness: Freshness,
    val sotMeta: DbMeta?,              // metadata decoded from SOT row (e.g., updatedAt, etag)
    val status: KeyStatus              // from Bookkeeper: lastSuccess/Failure/backoff/etag
)

/**
 * Resulting plan for the fetch step.
 */
sealed interface FetchPlan {
    /**
     * Skip fetching - serve local data only.
     */
    data object Skip : FetchPlan

    /**
     * Perform conditional fetch with If-None-Match/If-Modified-Since headers.
     */
    data class Conditional(val request: ConditionalRequest) : FetchPlan

    /**
     * Force full fetch - ignore cache.
     */
    data object Unconditional : FetchPlan
}

/**
 * Validator that determines fetch strategy based on freshness requirements.
 */
fun interface FreshnessValidator<K : StoreKey, DbMeta> {
    /**
     * Plans the fetch strategy based on freshness context.
     *
     * @param ctx Context including key, freshness policy, cached metadata, and key status
     * @return Fetch plan (Skip, Conditional, or Unconditional)
     */
    fun plan(ctx: FreshnessContext<K, DbMeta>): FetchPlan
}

/**
 * Default database metadata with updatedAt timestamp and optional etag.
 */
data class DefaultDbMeta(
    val updatedAt: Instant,
    val etag: String? = null
)

/**
 * Default freshness validator using TTL-based caching.
 *
 * @param ttl Time-to-live for cached data (e.g., 5.minutes)
 * @param staleIfError Duration to allow stale data on error (default: 10.minutes)
 */
class DefaultFreshnessValidator<K : StoreKey>(
    private val ttl: Duration,
    private val staleIfError: Duration? = 10.minutes
) : FreshnessValidator<K, DefaultDbMeta> {

    override fun plan(ctx: FreshnessContext<K, DefaultDbMeta>): FetchPlan {
        val age = ctx.sotMeta?.let { ctx.now - it.updatedAt }
        val backoffActive = ctx.status.backoffUntil?.let { ctx.now < it } == true

        if (backoffActive) return FetchPlan.Skip

        return when (ctx.freshness) {
            Freshness.CachedOrFetch -> {
                when {
                    ctx.sotMeta == null -> unconditional()
                    age != null && age <= ttl -> FetchPlan.Skip
                    else -> conditional(ctx.sotMeta.etag, ctx.sotMeta.updatedAt)
                }
            }
            is Freshness.MinAge -> {
                val maxAge = ctx.freshness.notOlderThan
                if (age == null || age > maxAge) conditional(ctx.sotMeta?.etag, ctx.sotMeta?.updatedAt)
                else FetchPlan.Skip
            }
            Freshness.MustBeFresh -> unconditional()
            Freshness.StaleIfError -> {
                // Prefer conditional to save bytes; if network fails upstream, Store serves stale.
                conditional(ctx.sotMeta?.etag, ctx.sotMeta?.updatedAt)
            }
        }
    }

    private fun unconditional() = FetchPlan.Unconditional
    private fun conditional(etag: String?, lastModified: Instant?) =
        if (etag != null || lastModified != null)
            FetchPlan.Conditional(ConditionalRequest(etag = etag, lastModified = lastModified))
        else FetchPlan.Unconditional
}
