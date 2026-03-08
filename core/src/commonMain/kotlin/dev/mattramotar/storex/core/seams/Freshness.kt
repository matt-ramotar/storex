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
            Freshness.StaleIfError -> conditional(ctx.sotMeta?.etag, ctx.sotMeta?.updatedAt)
        }
    }

    private fun unconditional() = FetchPlan.Unconditional

    private fun conditional(etag: String?, lastModified: Instant?) =
        if (etag != null || lastModified != null) {
            FetchPlan.Conditional(ConditionalRequest(etag = etag, lastModified = lastModified))
        } else {
            FetchPlan.Unconditional
        }
}
