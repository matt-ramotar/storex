package dev.mattramotar.storex.paging.internal

import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.internal.ConditionalRequest
import dev.mattramotar.storex.core.internal.DefaultDbMeta
import dev.mattramotar.storex.core.internal.FetchPlan
import dev.mattramotar.storex.core.internal.FreshnessContext
import dev.mattramotar.storex.core.internal.FreshnessValidator
import kotlin.time.Duration

class PageFreshnessValidator<K : StoreKey>(
    private val pageTtl: Duration
) : FreshnessValidator<K, DefaultDbMeta> {
    override fun plan(ctx: FreshnessContext<K, DefaultDbMeta>): FetchPlan {
        val meta = ctx.sotMeta
        return if (meta == null || (ctx.now - meta.updatedAt) > pageTtl)
            FetchPlan.Conditional(ConditionalRequest(etag = meta?.etag))
        else FetchPlan.Skip
    }
}
