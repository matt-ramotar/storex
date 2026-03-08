package dev.mattramotar.storex.paging.seams

import dev.mattramotar.storex.core.Freshness
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

    @Test
    fun cachedOrFetch_withFreshPage_skipsFetch() {
        val validator = PageFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(pageTtl = 5.minutes)

        val plan = validator.plan(
            FreshnessContext(
                key = key,
                now = now,
                freshness = Freshness.CachedOrFetch,
                sotMeta = DefaultDbMeta(updatedAt = now - 1.minutes, etag = "etag-1"),
                status = status
            )
        )

        assertEquals(FetchPlan.Skip, plan)
    }

    @Test
    fun minAge_withStalePage_usesConditionalPlan() {
        val validator = PageFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(pageTtl = 5.minutes)

        val plan = validator.plan(
            FreshnessContext(
                key = key,
                now = now,
                freshness = Freshness.MinAge(2.minutes),
                sotMeta = DefaultDbMeta(updatedAt = now - 10.minutes, etag = "etag-2"),
                status = status
            )
        )

        val conditional = assertIs<FetchPlan.Conditional>(plan)
        assertEquals("etag-2", conditional.request.etag)
    }

    @Test
    fun mustBeFresh_ignoresRecentPageAndFetchesUnconditionally() {
        val validator = PageFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(pageTtl = 5.minutes)

        val plan = validator.plan(
            FreshnessContext(
                key = key,
                now = now,
                freshness = Freshness.MustBeFresh,
                sotMeta = DefaultDbMeta(updatedAt = now - 1.minutes, etag = "etag-3"),
                status = status
            )
        )

        assertEquals(FetchPlan.Unconditional, plan)
    }
}
