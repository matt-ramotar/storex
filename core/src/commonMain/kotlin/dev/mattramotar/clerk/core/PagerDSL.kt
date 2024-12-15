package dev.mattramotar.clerk.core

import dev.mattramotar.clerk.coroutines.io
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.StoreBuilder


/**
 * ```kotlin
 * val pager = pager<Int, User> {
 *     pagingConfig {
 *         initialKey = 1
 *         pageSize = 50
 *     }
 *
 *     fetcher { pageKey ->
 *         api.fetchUsers(pageKey, 50)
 *     }
 *
 *     // Optional local caching
 *     sourceOfTruth(
 *         reader = { key -> userDao.getUsers(key) },
 *         writer = { key, users -> userDao.insertUsers(key, users) },
 *         delete = { key -> userDao.deleteUsersForPage(key) },
 *         deleteAll = { userDao.clearAll() }
 *     )
 *
 *     // Optional telemetry and events
 *     telemetryCollector(MyTelemetry())
 *     eventsListener(MyEvents())
 *
 *     // Optional coroutine scopes
 *     mainScope(myCustomMainScope)
 *     ioDispatcher(myCustomDispatcher)
 * }
 * ```
 */
inline fun <Key: Any, Value: Any> pager(
    builderBlock: PagerDSL<Key, Value>.() -> Unit
): Pager<Key, Value> {
    val dsl = PagerDSL<Key, Value>().apply(builderBlock)
    return dsl.buildPager()
}

class PagerDSL<Key: Any, Value: Any> {
    private var pagingConfig: PagingConfig<Key>? = null
    private var fetcher: (suspend (Key) -> List<Value>)? = null
    private var sourceOfTruth: SourceOfTruth<Key, List<Value>, List<Value>>? = null
    private var nextKeyProvider: NextKeyProvider<Key, Value>? = null
    private var telemetryCollector: PagingTelemetryCollector<Key, Value> = NoOpPagingTelemetryCollector()
    private var eventsListener: PagerEventsListener<Key, Value> = NoOpPagerEventsListener()
    private var mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.io

    fun pagingConfig(block: PagingConfigBuilder<Key>.() -> Unit) {
        val builder = PagingConfigBuilder<Key>().apply(block)
        pagingConfig = builder.build()
    }

    fun fetcher(fetch: suspend (Key) -> List<Value>) {
        this.fetcher = fetch
    }

    fun sourceOfTruth(
        reader: suspend (Key) -> List<Value>?,
        writer: suspend (Key, List<Value>) -> Unit,
        delete: (suspend (Key) -> Unit)? = null,
        deleteAll: (suspend () -> Unit)? = null,
    ) {
        this.sourceOfTruth = SimpleSourceOfTruth(
            read = reader,
            writer = writer,
            delete = delete,
            deleteAll = deleteAll
        )
    }

    fun nextKeyProvider(provider: NextKeyProvider<Key, Value>) {
        this.nextKeyProvider = provider
    }

    fun telemetryCollector(collector: PagingTelemetryCollector<Key, Value>) {
        this.telemetryCollector = collector
    }

    fun eventsListener(listener: PagerEventsListener<Key, Value>) {
        this.eventsListener = listener
    }

    fun mainScope(scope: CoroutineScope) {
        this.mainScope = scope
    }

    fun ioDispatcher(dispatcher: CoroutineDispatcher) {
        this.ioDispatcher = dispatcher
    }

    @PublishedApi
    internal fun buildPager(): Pager<Key, Value> {
        val config = requireNotNull(pagingConfig) {
            "pagingConfig is required. Please provide a pagingConfig block."
        }
        val fetch = requireNotNull(fetcher) {
            "fetcher is required. Please provide a fetcher lambda."
        }

        val sot = sourceOfTruth


        val storeBuilder = if (sot != null) {
            // Build store using sourceOfTruth
            StoreBuilder.from(fetcher = Fetcher.of(fetch = fetch), sourceOfTruth = sot)
        } else {
            // Build store without sourceOfTruth
            StoreBuilder.from(Fetcher.of(fetch = fetch))
        }

        // If user hasn't provided a nextKeyProvider and the key is a known type, pick a default:
        val finalNextKeyProvider = nextKeyProvider ?: defaultNextKeyProviderForNumericKey(config.initialKey)

        val store = storeBuilder.build()

        return DefaultPager(
            store = store,
            pagingConfig = config,
            nextKeyProvider = finalNextKeyProvider,
            telemetryCollector = telemetryCollector,
            eventsListener = eventsListener,
            mainCoroutineScope = mainScope,
            ioDispatcher = ioDispatcher
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun defaultNextKeyProviderForNumericKey(key: Key): NextKeyProvider<Key, Value> {
        return when (key) {
            is Int -> IntNextKeyProvider<Value>() as NextKeyProvider<Key, Value>
            is Long -> LongNextKeyProvider<Value>() as NextKeyProvider<Key, Value>
            is Double -> DoubleNextKeyProvider<Value>() as NextKeyProvider<Key, Value>
            else -> throw IllegalArgumentException("${key::class.simpleName} does not have a default KeyProvider. You will need to provide one.")
        }
    }
}

class PagingConfigBuilder<Key: Any> {
    var initialKey: Key? = null
    var pageSize: Int = 20

    fun build(): PagingConfig<Key> {
        val initKey = requireNotNull(initialKey) {
            "initialKey is required in pagingConfig."
        }
        return PagingConfig(initialKey = initKey, pageSize = pageSize)
    }
}

// A simple SourceOfTruth adapter to keep the DSL straightforward
class SimpleSourceOfTruth<Key: Any, Value: Any>(
    private val read: suspend (Key) -> Value?,
    private val writer: suspend (Key, Value) -> Unit,
    private val delete: (suspend (Key) -> Unit)? = null,
    private val deleteAll: (suspend () -> Unit)? = null
): SourceOfTruth<Key, Value, Value> {
    override fun reader(key: Key): Flow<Value?> = flow {
        emit(read(key))
    }
    override suspend fun write(key: Key, value: Value) = writer(key, value)
    override suspend fun delete(key: Key) { delete?.invoke(key) }
    override suspend fun deleteAll() { deleteAll?.invoke() }
}


