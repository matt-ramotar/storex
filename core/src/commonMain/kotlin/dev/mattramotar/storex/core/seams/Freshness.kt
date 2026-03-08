package dev.mattramotar.storex.core.seams

import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.StoreKey
import kotlinx.datetime.Instant
import kotlin.time.Duration

data class FreshnessContext<K : StoreKey, DbMeta>(
    val key: K,
    val now: Instant,
    val freshness: Freshness,
    val sotMeta: DbMeta?,
    val status: KeyStatus
)

sealed interface FetchPlan {
    data object Skip : FetchPlan
    data class Conditional(val request: ConditionalRequest) : FetchPlan
    data object Unconditional : FetchPlan
}

fun interface FreshnessValidator<K : StoreKey, DbMeta> {
    fun plan(ctx: FreshnessContext<K, DbMeta>): FetchPlan
}

data class DefaultDbMeta(
    val updatedAt: Instant,
    val etag: String? = null
)

class DefaultFreshnessValidator<K : StoreKey>(
    private val ttl: Duration,
) : FreshnessValidator<K, DefaultDbMeta> {

    override fun plan(ctx: FreshnessContext<K, DefaultDbMeta>): FetchPlan {
        val backoffUntil = ctx.status.backoffUntil
        if (backoffUntil != null) {
            if (ctx.now < backoffUntil) return FetchPlan.Skip
        }

        return when (ctx.freshness) {
            Freshness.CachedOrFetch -> cachedOrFetch(ctx)

            is Freshness.MinAge -> {
                minAge(ctx, ctx.freshness.notOlderThan)
            }

            Freshness.MustBeFresh -> unconditional()
            Freshness.StaleIfError -> conditional(ctx.sotMeta?.etag, ctx.sotMeta?.updatedAt)
        }
    }

    private fun cachedOrFetch(ctx: FreshnessContext<K, DefaultDbMeta>): FetchPlan {
        val meta = ctx.sotMeta ?: return unconditional()
        val age = ctx.now - meta.updatedAt
        return if (age <= ttl) FetchPlan.Skip else conditional(meta.etag, meta.updatedAt)
    }

    private fun minAge(
        ctx: FreshnessContext<K, DefaultDbMeta>,
        maxAge: Duration
    ): FetchPlan {
        val meta = ctx.sotMeta ?: return unconditional()
        val age = ctx.now - meta.updatedAt
        return if (age > maxAge) conditional(meta.etag, meta.updatedAt) else FetchPlan.Skip
    }

    private fun unconditional() = FetchPlan.Unconditional

    private fun conditional(etag: String?, lastModified: Instant?): FetchPlan {
        if (etag == null && lastModified == null) return FetchPlan.Unconditional
        return FetchPlan.Conditional(ConditionalRequest(etag = etag, lastModified = lastModified))
    }
}
