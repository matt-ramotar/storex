package dev.mattramotar.storex.paging.internal

import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.seams.ConditionalRequest
import dev.mattramotar.storex.core.seams.DefaultDbMeta
import dev.mattramotar.storex.core.seams.FetchPlan
import dev.mattramotar.storex.core.seams.FreshnessContext
import dev.mattramotar.storex.core.seams.FreshnessValidator
import kotlinx.datetime.Instant
import kotlin.time.Duration

class PageFreshnessValidator<K : StoreKey>(
    private val pageTtl: Duration
) : FreshnessValidator<K, DefaultDbMeta> {
    override fun plan(ctx: FreshnessContext<K, DefaultDbMeta>): FetchPlan {
        val freshness = ctx.freshness
        val backoffUntil = ctx.status.backoffUntil

        if (backoffUntil != null) {
            if (ctx.now < backoffUntil) return FetchPlan.Skip
        }

        return when (freshness) {
            Freshness.CachedOrFetch -> cachedOrFetch(ctx)

            is Freshness.MinAge -> {
                minAge(ctx, freshness.notOlderThan)
            }

            Freshness.MustBeFresh -> FetchPlan.Unconditional
            Freshness.StaleIfError -> conditional(ctx.sotMeta?.etag, ctx.sotMeta?.updatedAt)
        }
    }

    private fun cachedOrFetch(ctx: FreshnessContext<K, DefaultDbMeta>): FetchPlan {
        val meta = ctx.sotMeta ?: return FetchPlan.Unconditional
        val age = ctx.now - meta.updatedAt
        return if (age <= pageTtl) FetchPlan.Skip else conditional(meta.etag, meta.updatedAt)
    }

    private fun minAge(
        ctx: FreshnessContext<K, DefaultDbMeta>,
        maxAge: Duration
    ): FetchPlan {
        val meta = ctx.sotMeta ?: return FetchPlan.Unconditional
        val age = ctx.now - meta.updatedAt
        return if (age > maxAge) conditional(meta.etag, meta.updatedAt) else FetchPlan.Skip
    }

    private fun conditional(etag: String?, lastModified: Instant?): FetchPlan {
        if (etag == null && lastModified == null) return FetchPlan.Unconditional
        return FetchPlan.Conditional(ConditionalRequest(etag = etag, lastModified = lastModified))
    }
}
