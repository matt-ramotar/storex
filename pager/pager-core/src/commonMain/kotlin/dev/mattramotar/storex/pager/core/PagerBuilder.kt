package dev.mattramotar.storex.pager.core

import dev.mattramotar.storex.pager.coroutines.io
import dev.mattramotar.storex.pager.store.LoadDirection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import kotlin.jvm.JvmName

/**
 * A builder for creating a Pager from a Store (or StoreBuilder).
 * This allows the user to configure paging parameters and optional telemetry and event listeners
 * without needing to know the details of the underlying pager implementation.
 */
class PagerBuilder<Key : Any, Value : Any> internal constructor(
    private val storeBuilder: (() -> Store<Key, List<Value>>)?,
    private val store: Store<Key, List<Value>>?,
    internal var pagingConfig: PagingConfig<Key>,
    internal var nextKeyProvider: NextKeyProvider<Key, Value>,
    internal var telemetryCollector: PagingTelemetryCollector<Key, Value> = NoOpPagingTelemetryCollector(),
    internal var eventsListener: PagerEventsListener<Key, Value> = NoOpPagerEventsListener(),
    internal var mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    internal var mainCoroutineScope: CoroutineScope = CoroutineScope(mainDispatcher),
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.io
) {
    init {
        require(storeBuilder != null || store != null) {
            "PagerBuilder requires either a StoreBuilder (via storeBuilder) or an existing Store."
        }
    }

    fun pagingConfig(pagingConfig: PagingConfig<Key>) = apply {
        this.pagingConfig = pagingConfig
    }

    fun nextKeyProvider(nextKeyProvider: NextKeyProvider<Key, Value>) = apply {
        this.nextKeyProvider = nextKeyProvider
    }

    fun telemetryCollector(telemetryCollector: PagingTelemetryCollector<Key, Value>) = apply {
        this.telemetryCollector = telemetryCollector
    }

    fun eventsListener(eventsListener: PagerEventsListener<Key, Value>) = apply {
        this.eventsListener = eventsListener
    }

    fun mainCoroutineScope(scope: CoroutineScope) = apply {
        this.mainCoroutineScope = scope
    }

    fun ioDispatcher(dispatcher: CoroutineDispatcher) = apply {
        this.ioDispatcher = dispatcher
    }

    /**
     * Builds and returns a Pager instance.
     * If a StoreBuilder was provided, it will first build the Store, then create the Pager.
     * If a Store was directly provided, it will use that store.
     */
    fun build(): Pager<Key, Value> {
        val builtStore = store ?: storeBuilder!!.invoke()
        return DefaultPager(
            store = builtStore,
            pagingConfig = pagingConfig,
            nextKeyProvider = nextKeyProvider,
            telemetryCollector = telemetryCollector,
            eventsListener = eventsListener,
            mainCoroutineScope = mainCoroutineScope,
            ioDispatcher = ioDispatcher
        )
    }
}

/**
 * Extension function on StoreBuilder to start constructing a Pager.
 *
 * The user does not need to manually build the store first; this builder will handle that.
 */
fun <Key : Any, Value : Any> StoreBuilder<Key, List<Value>>.toPagerBuilder(
    pagingConfig: PagingConfig<Key>,
    nextKeyProvider: NextKeyProvider<Key, Value>,
): PagerBuilder<Key, Value> {
    return PagerBuilder(
        storeBuilder = { this.build() },
        store = null,
        pagingConfig = pagingConfig,
        nextKeyProvider = nextKeyProvider
    )
}

/**
 * Extension function on Store to start constructing a Pager.
 *
 * If the user already has a Store, they can convert it directly into a PagerBuilder.
 */
fun <Key : Any, Value : Any> Store<Key, List<Value>>.toPagerBuilder(
    pagingConfig: PagingConfig<Key>,
    nextKeyProvider: NextKeyProvider<Key, Value>,
): PagerBuilder<Key, Value> {
    return PagerBuilder(
        storeBuilder = null,
        store = this,
        pagingConfig = pagingConfig,
        nextKeyProvider = nextKeyProvider
    )
}

fun <Key : Any, Value : Any> Store<Key, List<Value>>.toPager(
    pagingConfig: PagingConfig<Key>,
    nextKeyProvider: NextKeyProvider<Key, Value>,
    builder: PagerBuilder<Key, Value>.() -> Unit
): Pager<Key, Value> {
    return PagerBuilder(
        storeBuilder = null,
        store = this,
        pagingConfig = pagingConfig,
        nextKeyProvider = nextKeyProvider
    ).apply(builder).build()
}


@JvmName("toIntPager")
fun <Value : Any> Store<Int, List<Value>>.toPager(
    pagingConfig: PagingConfig<Int>,
    nextKeyProvider: NextKeyProvider<Int, Value> = IntNextKeyProvider(),
    builder: PagerBuilder<Int, Value>.() -> Unit,
): Pager<Int, Value> {
    return PagerBuilder(
        storeBuilder = null,
        store = this,
        pagingConfig = pagingConfig,
        nextKeyProvider = nextKeyProvider
    ).apply(builder).build()
}

@JvmName("toLongPager")
fun <Value : Any> Store<Long, List<Value>>.toPager(
    pagingConfig: PagingConfig<Long>,
    nextKeyProvider: NextKeyProvider<Long, Value> = LongNextKeyProvider(),
    builder: PagerBuilder<Long, Value>.() -> Unit,
): Pager<Long, Value> {
    return PagerBuilder(
        storeBuilder = null,
        store = this,
        pagingConfig = pagingConfig,
        nextKeyProvider = nextKeyProvider
    ).apply(builder).build()
}

@JvmName("toDoublePager")
fun <Value : Any> Store<Double, List<Value>>.toPager(
    pagingConfig: PagingConfig<Double>,
    nextKeyProvider: NextKeyProvider<Double, Value> = DoubleNextKeyProvider(),
    builder: PagerBuilder<Double, Value>.() -> Unit,
): Pager<Double, Value> {
    return PagerBuilder(
        storeBuilder = null,
        store = this,
        pagingConfig = pagingConfig,
        nextKeyProvider = nextKeyProvider
    ).apply(builder).build()
}


/**
 * A NextKeyProvider for Int keys that increments or decrements by the pageSize.
 * If the loadedItems are empty, it indicates no further data is available in that direction.
 */
class IntNextKeyProvider<Value : Any> : NextKeyProvider<Int, Value> {
    override fun computeNextKey(
        currentKey: Int,
        direction: LoadDirection,
        loadedItems: List<Value>
    ): Int {
        // If we got fewer items than pageSize, no more pages in this direction.
        if (loadedItems.isEmpty()) return currentKey

        return when (direction) {
            LoadDirection.Append -> currentKey + loadedItems.size
            LoadDirection.Prepend -> (currentKey - loadedItems.size).coerceAtLeast(0)
        }
    }
}

/**
 * A NextKeyProvider for Long keys.
 */
class LongNextKeyProvider<Value : Any> : NextKeyProvider<Long, Value> {
    override fun computeNextKey(
        currentKey: Long,
        direction: LoadDirection,
        loadedItems: List<Value>
    ): Long {
        if (loadedItems.isEmpty()) return currentKey

        val increment = loadedItems.size.toLong()
        return when (direction) {
            LoadDirection.Append -> currentKey + increment
            LoadDirection.Prepend -> (currentKey - increment).coerceAtLeast(0L)
        }
    }
}

/**
 * A NextKeyProvider for Double keys, though this is less common.
 * We'll assume a fixed increment/decrement step equal to the number of loaded items.
 */
class DoubleNextKeyProvider<Value : Any> : NextKeyProvider<Double, Value> {
    override fun computeNextKey(
        currentKey: Double,
        direction: LoadDirection,
        loadedItems: List<Value>
    ): Double {
        if (loadedItems.isEmpty()) return currentKey

        val increment = loadedItems.size.toDouble()
        return when (direction) {
            LoadDirection.Append -> currentKey + increment
            LoadDirection.Prepend -> (currentKey - increment).coerceAtLeast(0.0)
        }
    }
}

fun <Key : Any, Value : Any> PagerBuilder<Key, Value>.nextKeyProvider(
    block: (currentKey: Key, direction: LoadDirection, loadedItems: List<Value>) -> Key
) = apply {
    this.nextKeyProvider = object : NextKeyProvider<Key, Value> {
        override fun computeNextKey(currentKey: Key, direction: LoadDirection, loadedItems: List<Value>): Key {
            return block(currentKey, direction, loadedItems)
        }
    }
}

fun <Key : Any, Value : Any> PagerBuilder<Key, Value>.noTelemetry() = apply {
    this.telemetryCollector = NoOpPagingTelemetryCollector()
}

fun <Key : Any, Value : Any> PagerBuilder<Key, Value>.noEvents() = apply {
    this.eventsListener = NoOpPagerEventsListener()
}

fun <Key : Any, Value : Any> PagerBuilder<Key, Value>.defaultDispatchers() = apply {
    this.ioDispatcher = Dispatchers.io
    this.mainDispatcher = Dispatchers.Main
    this.mainCoroutineScope = CoroutineScope(this.mainDispatcher)
}

fun <Value : Any> pagerForIntStore(
    store: Store<Int, List<Value>>,
    initialKey: Int,
    pageSize: Int,
    builder: PagerBuilder<Int, Value>.() -> Unit = {}
): Pager<Int, Value> {
    return store.toPager(
        pagingConfig = PagingConfig(initialKey, pageSize),
        nextKeyProvider = IntNextKeyProvider(),
        builder = builder
    )
}