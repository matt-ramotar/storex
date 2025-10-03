package dev.mattramotar.storex.store.page.internal

import dev.mattramotar.storex.store.StoreKey
import dev.mattramotar.storex.store.internal.ConditionalRequest
import dev.mattramotar.storex.store.internal.DefaultDbMeta
import dev.mattramotar.storex.store.internal.FetchPlan
import dev.mattramotar.storex.store.internal.FreshnessContext
import dev.mattramotar.storex.store.internal.FreshnessValidator
import kotlin.time.Duration

class PageFreshnessValidator<K : StoreKey>(
    private val pageTtl: Duration
) : FreshnessValidator<K, DefaultDbMeta> {
    override fun plan(ctx: FreshnessContext<K, DefaultDbMeta>) =
        if (ctx.sotMeta == null || (ctx.now - ctx.sotMeta.updatedAt) > pageTtl)
            FetchPlan.Conditional(ConditionalRequest(etag = ctx.sotMeta?.etag))
        else FetchPlan.Skip
}

