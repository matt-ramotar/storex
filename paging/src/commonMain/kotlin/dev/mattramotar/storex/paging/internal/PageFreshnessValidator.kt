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
        val meta = ctx.sotMeta
        val age = meta?.let { ctx.now - it.updatedAt }
        val freshness = ctx.freshness

        return when (freshness) {
            Freshness.CachedOrFetch -> {
                when {
                    meta == null -> FetchPlan.Unconditional
                    age != null && age <= pageTtl -> FetchPlan.Skip
                    else -> conditional(meta.etag, meta.updatedAt)
                }
            }

            is Freshness.MinAge -> {
                val maxAge = freshness.notOlderThan
                if (age == null || age > maxAge) conditional(meta?.etag, meta?.updatedAt)
                else FetchPlan.Skip
            }

            Freshness.MustBeFresh -> FetchPlan.Unconditional
            Freshness.StaleIfError -> conditional(meta?.etag, meta?.updatedAt)
        }
    }

    private fun conditional(etag: String?, lastModified: Instant?) =
        if (etag != null || lastModified != null) {
            FetchPlan.Conditional(ConditionalRequest(etag = etag, lastModified = lastModified))
        } else {
            FetchPlan.Unconditional
        }
}
