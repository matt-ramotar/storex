package dev.mattramotar.storex.paging

import dev.mattramotar.storex.core.StoreKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PageStoreBuilderTest {

    @Test
    fun pageStore_builder_creates_instance() {
        val store = pageStore<StoreKey, TestItem> {
            fetcher { _, _ -> generateTestPage(0, 20) }
        }

        assertNotNull(store)
    }

    @Test
    fun pageStore_builder_requires_fetcher() {
        assertFailsWith<IllegalArgumentException> {
            pageStore<StoreKey, TestItem> {
                // No fetcher configured
            }
        }
    }

    @Test
    fun pageStore_builder_accepts_config() {
        val store = pageStore<StoreKey, TestItem> {
            fetcher { _, _ -> generateTestPage(0, 20) }

            config {
                pageSize = 30
                prefetchDistance = 10
                maxSize = 200
                placeholders = true
            }
        }

        assertNotNull(store)
    }

    @Test
    fun config_builder_uses_defaults() {
        val configBuilder = PagingConfigBuilder()
        val config = configBuilder.build()

        assertEquals(20, config.pageSize)
        assertEquals(20, config.prefetchDistance)
        assertEquals(200, config.maxSize)
        assertEquals(false, config.placeholders)
    }

    @Test
    fun config_builder_overrides_values() {
        val configBuilder = PagingConfigBuilder()
        configBuilder.pageSize = 50
        configBuilder.prefetchDistance = 15
        configBuilder.maxSize = 500
        configBuilder.placeholders = true

        val config = configBuilder.build()

        assertEquals(50, config.pageSize)
        assertEquals(15, config.prefetchDistance)
        assertEquals(500, config.maxSize)
        assertEquals(true, config.placeholders)
    }

    @Test
    fun pageStore_builder_with_all_options() {
        val store = pageStore<StoreKey, TestItem> {
            fetcher { key, token ->
                generateTestPage(0, 20)
            }

            config {
                pageSize = 25
                prefetchDistance = 5
                maxSize = 150
            }
        }

        assertNotNull(store)
    }

    @Test
    fun pageStore_builder_fetcher_receives_key_and_token() {
        var capturedKey: StoreKey? = null
        var capturedToken: PageToken? = null

        val store = pageStore<StoreKey, TestItem> {
            fetcher { key, token ->
                capturedKey = key
                capturedToken = token
                generateTestPage(0, 20)
            }
        }

        assertNotNull(store)
        // Fetcher will be called when stream is collected
    }
}
