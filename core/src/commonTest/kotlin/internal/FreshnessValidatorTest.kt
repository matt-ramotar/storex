package dev.mattramotar.storex.core.internal

import dev.mattramotar.storex.core.Freshness
import dev.mattramotar.storex.core.utils.TEST_KEY_1
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FreshnessValidatorTest {

    @Test
    fun plan_cachedOrFetch_givenNoCache_thenUnconditional() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 5.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val context = FreshnessContext<dev.mattramotar.storex.core.StoreKey, DefaultDbMeta>(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.CachedOrFetch,
            sotMeta = null, // No cached data
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertEquals(FetchPlan.Unconditional, plan)
    }

    @Test
    fun plan_cachedOrFetch_givenFreshCache_thenSkip() = runTest {
        // Given
        val ttl = 5.minutes
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(ttl = ttl)
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 2.minutes // Cached 2 minutes ago (within TTL)

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.CachedOrFetch,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertEquals(FetchPlan.Skip, plan)
    }

    @Test
    fun plan_cachedOrFetch_givenStaleCache_thenConditional() = runTest {
        // Given
        val ttl = 5.minutes
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(ttl = ttl)
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 10.minutes // Cached 10 minutes ago (beyond TTL)

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.CachedOrFetch,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = "etag-123"),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertIs<FetchPlan.Conditional>(plan)
        assertEquals("etag-123", plan.request.etag)
        assertEquals(cachedAt, plan.request.lastModified)
    }

    @Test
    fun plan_cachedOrFetch_givenStaleWithoutEtag_thenUnconditional() = runTest {
        // Given
        val ttl = 5.minutes
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(ttl = ttl)
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 10.minutes

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.CachedOrFetch,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = null),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then - Uses lastModified for conditional request (If-Modified-Since)
        assertIs<FetchPlan.Conditional>(plan)
        assertEquals(null, plan.request.etag)
        assertEquals(cachedAt, plan.request.lastModified)
    }

    @Test
    fun plan_minAge_givenCacheNewerThanThreshold_thenSkip() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 1.minutes // 1 minute old

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.MinAge(notOlderThan = 5.minutes), // Must be < 5 min old
            sotMeta = DefaultDbMeta(updatedAt = cachedAt),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertEquals(FetchPlan.Skip, plan)
    }

    @Test
    fun plan_minAge_givenCacheOlderThanThreshold_thenConditional() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 10.minutes // 10 minutes old

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.MinAge(notOlderThan = 5.minutes), // Must be < 5 min old
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = "etag-123"),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertIs<FetchPlan.Conditional>(plan)
        assertEquals("etag-123", plan.request.etag)
    }

    @Test
    fun plan_minAge_givenNoCache_thenConditional() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)

        val context = FreshnessContext<dev.mattramotar.storex.core.StoreKey, DefaultDbMeta>(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.MinAge(notOlderThan = 5.minutes),
            sotMeta = null,
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertEquals(FetchPlan.Unconditional, plan)
    }

    @Test
    fun plan_mustBeFresh_thenAlwaysUnconditional() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 1.seconds // Very fresh cache

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.MustBeFresh,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = "etag-123"),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertEquals(FetchPlan.Unconditional, plan)
    }

    @Test
    fun plan_staleIfError_thenConditional() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 5.minutes

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.StaleIfError,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = "etag-123"),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertIs<FetchPlan.Conditional>(plan)
        assertEquals("etag-123", plan.request.etag)
    }

    @Test
    fun plan_staleIfError_withoutEtag_thenUnconditional() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 5.minutes

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.StaleIfError,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = null),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then - Uses lastModified for conditional request (If-Modified-Since)
        assertIs<FetchPlan.Conditional>(plan)
        assertEquals(null, plan.request.etag)
        assertEquals(cachedAt, plan.request.lastModified)
    }

    @Test
    fun plan_givenBackoffActive_thenSkip() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val backoffUntil = now + 5.minutes // Backoff active for 5 more minutes

        val context = FreshnessContext<dev.mattramotar.storex.core.StoreKey, DefaultDbMeta>(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.MustBeFresh, // Even for MustBeFresh
            sotMeta = null,
            status = KeyStatus(null, null, null, backoffUntil)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertEquals(FetchPlan.Skip, plan)
    }

    @Test
    fun plan_givenBackoffExpired_thenProceeds() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val backoffUntil = now - 1.minutes // Backoff expired 1 minute ago

        val context = FreshnessContext<dev.mattramotar.storex.core.StoreKey, DefaultDbMeta>(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.MustBeFresh,
            sotMeta = null,
            status = KeyStatus(null, null, null, backoffUntil)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertEquals(FetchPlan.Unconditional, plan)
    }

    @Test
    fun plan_conditionalRequest_includesLastModified() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 5.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 10.minutes

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.CachedOrFetch,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = null),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then - no etag but has lastModified
        assertIs<FetchPlan.Conditional>(plan)
        assertEquals(cachedAt, plan.request.lastModified)
    }

    @Test
    fun plan_conditionalRequest_includesBothEtagAndLastModified() = runTest {
        // Given
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 5.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 10.minutes
        val etag = "etag-abc"

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.CachedOrFetch,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = etag),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertIs<FetchPlan.Conditional>(plan)
        assertEquals(etag, plan.request.etag)
        assertEquals(cachedAt, plan.request.lastModified)
    }

    @Test
    fun plan_staleIfError_withCustomTTL_thenUsesStaleWindow() = runTest {
        // Given - staleIfError window is 10 minutes
        val validator = DefaultFreshnessValidator<dev.mattramotar.storex.core.StoreKey>(
            ttl = 5.minutes,
            staleIfError = 10.minutes
        )
        val now = Instant.fromEpochSeconds(1000)
        val cachedAt = now - 8.minutes // 8 minutes old (within stale window)

        val context = FreshnessContext(
            key = TEST_KEY_1,
            now = now,
            freshness = Freshness.StaleIfError,
            sotMeta = DefaultDbMeta(updatedAt = cachedAt, etag = "etag-123"),
            status = KeyStatus(null, null, null, null)
        )

        // When
        val plan = validator.plan(context)

        // Then
        assertIs<FetchPlan.Conditional>(plan)
    }
}
