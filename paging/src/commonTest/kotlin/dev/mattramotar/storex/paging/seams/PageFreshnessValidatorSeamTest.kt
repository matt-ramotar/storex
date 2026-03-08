package dev.mattramotar.storex.paging.seams

import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.seams.DefaultDbMeta
import dev.mattramotar.storex.core.seams.FetchPlan
import dev.mattramotar.storex.core.seams.FreshnessContext
import dev.mattramotar.storex.core.seams.KeyStatus
import dev.mattramotar.storex.paging.TestKey
import dev.mattramotar.storex.paging.internal.PageFreshnessValidator
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class PageFreshnessValidatorSeamTest {

    private val key = TestKey()
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val status = KeyStatus(
        lastSuccessAt = null,
        lastFailureAt = null,
        lastEtag = null,
        backoffUntil = null
    )

    private fun validator() = PageFreshnessValidator<StoreKey>(pageTtl = 5.minutes)

    private fun context(
        freshness: Freshness,
        meta: DefaultDbMeta?
    ) = FreshnessContext(
        key = key,
        now = now,
        freshness = freshness,
        sotMeta = meta,
        status = status
    )

    @Test
    fun cachedOrFetch_withFreshPage_skipsFetch() {
        val plan = validator().plan(
            context(
                freshness = Freshness.CachedOrFetch,
                meta = DefaultDbMeta(updatedAt = now - 1.minutes, etag = "etag-1")
            )
        )

        assertEquals(FetchPlan.Skip, plan)
    }

    @Test
    fun cachedOrFetch_withNoMetadata_fetchesUnconditionally() {
        val plan = validator().plan(
            context(
                freshness = Freshness.CachedOrFetch,
                meta = null
            )
        )

        assertEquals(FetchPlan.Unconditional, plan)
    }

    @Test
    fun cachedOrFetch_withStalePage_usesConditionalPlan() {
        val plan = validator().plan(
            context(
                freshness = Freshness.CachedOrFetch,
                meta = DefaultDbMeta(updatedAt = now - 10.minutes, etag = "etag-2")
            )
        )

        val conditional = assertIs<FetchPlan.Conditional>(plan)
        assertEquals("etag-2", conditional.request.etag)
    }

    @Test
    fun minAge_withFreshPage_skipsFetch() {
        val plan = validator().plan(
            context(
                freshness = Freshness.MinAge(2.minutes),
                meta = DefaultDbMeta(updatedAt = now - 1.minutes, etag = "etag-3")
            )
        )

        assertEquals(FetchPlan.Skip, plan)
    }

    @Test
    fun minAge_withStalePage_usesConditionalPlan() {
        val plan = validator().plan(
            context(
                freshness = Freshness.MinAge(2.minutes),
                meta = DefaultDbMeta(updatedAt = now - 10.minutes, etag = "etag-4")
            )
        )

        val conditional = assertIs<FetchPlan.Conditional>(plan)
        assertEquals("etag-4", conditional.request.etag)
    }

    @Test
    fun minAge_withoutMetadata_fetchesUnconditionally() {
        val plan = validator().plan(
            context(
                freshness = Freshness.MinAge(2.minutes),
                meta = null
            )
        )

        assertEquals(FetchPlan.Unconditional, plan)
    }

    @Test
    fun staleIfError_withValidators_usesConditionalPlan() {
        val plan = validator().plan(
            context(
                freshness = Freshness.StaleIfError,
                meta = DefaultDbMeta(updatedAt = now - 10.minutes, etag = "etag-5")
            )
        )

        val conditional = assertIs<FetchPlan.Conditional>(plan)
        assertEquals("etag-5", conditional.request.etag)
    }

    @Test
    fun staleIfError_withoutMetadata_fetchesUnconditionally() {
        val plan = validator().plan(
            context(
                freshness = Freshness.StaleIfError,
                meta = null
            )
        )

        assertEquals(FetchPlan.Unconditional, plan)
    }

    @Test
    fun mustBeFresh_ignoresRecentPageAndFetchesUnconditionally() {
        val plan = validator().plan(
            context(
                freshness = Freshness.MustBeFresh,
                meta = DefaultDbMeta(updatedAt = now - 1.minutes, etag = "etag-6")
            )
        )

        assertEquals(FetchPlan.Unconditional, plan)
    }
}
