# StoreX Interceptors

**Request/response interception for cross-cutting concerns**

The `:interceptors` module provides a flexible interception mechanism for StoreX operations. Intercept read/write operations to add authentication, logging, metrics, caching, and other cross-cutting concerns without modifying store logic.

> **Status**: 🚧 **Placeholder Implementation** - Full implementation planned for future release

## 📦 What's Included

This module will provide:

- **`Interceptor<Key, Value>`** - Base interceptor interface
- **`InterceptorChain`** - Chain-of-responsibility pattern
- **Built-in Interceptors** - Common interceptors (logging, auth, metrics)
- **Async Support** - Suspend functions for non-blocking interception
- **Error Handling** - Intercept and transform errors
- **Request/Response Transformation** - Modify data in transit

## 🎯 When to Use

Use this module when you need:

- **Authentication** - Add auth tokens to all requests
- **Logging** - Track all store operations
- **Metrics/Analytics** - Collect performance data
- **Caching** - Implement custom cache policies
- **Rate Limiting** - Throttle requests
- **Error Transformation** - Convert exceptions to domain errors
- **Request/Response Modification** - Transform data

**Perfect for:**
- Adding auth headers to GraphQL/REST requests
- Logging all cache hits/misses
- Tracking user analytics
- Implementing circuit breakers
- Adding custom retry logic

## 🚀 Planned Usage

```kotlin
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.interceptors.*

// Define custom interceptor
class AuthInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor<StoreKey, Any> {
    override suspend fun intercept(
        chain: InterceptorChain<StoreKey, Any>,
        request: StoreRequest<StoreKey>
    ): StoreResponse<Any> {
        // Add auth token to request
        val authenticatedRequest = request.copy(
            headers = request.headers + ("Authorization" to "Bearer ${tokenProvider.getToken()}")
        )
        return chain.proceed(authenticatedRequest)
    }
}

// Create store with interceptors
val userStore = store<ByIdKey, User> {
    fetcher { key ->
        flow {
            val user = api.getUser(key.entity.id)
            emit(FetcherResult.Success(user))
        }
    }

    interceptors {
        // Add interceptors in order of execution
        add(LoggingInterceptor())
        add(AuthInterceptor(tokenProvider))
        add(MetricsInterceptor())
        add(RetryInterceptor(maxRetries = 3))
    }
}
```

## 📚 Planned Features

### Logging Interceptor

```kotlin
class LoggingInterceptor : Interceptor<StoreKey, Any> {
    override suspend fun intercept(
        chain: InterceptorChain<StoreKey, Any>,
        request: StoreRequest<StoreKey>
    ): StoreResponse<Any> {
        val start = Clock.System.now()
        logger.debug("Store request: ${request.key}")

        return try {
            val response = chain.proceed(request)
            val duration = Clock.System.now() - start
            logger.debug("Store response: ${request.key} (${duration}ms)")
            response
        } catch (e: Exception) {
            val duration = Clock.System.now() - start
            logger.error("Store error: ${request.key} (${duration}ms)", e)
            throw e
        }
    }
}
```

### Metrics Interceptor

```kotlin
class MetricsInterceptor : Interceptor<StoreKey, Any> {
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)

    override suspend fun intercept(
        chain: InterceptorChain<StoreKey, Any>,
        request: StoreRequest<StoreKey>
    ): StoreResponse<Any> {
        val response = chain.proceed(request)

        when (response.origin) {
            Origin.Cache -> cacheHits.incrementAndGet()
            Origin.Network -> cacheMisses.incrementAndGet()
        }

        return response
    }

    fun hitRate(): Double = cacheHits.get().toDouble() / (cacheHits.get() + cacheMisses.get())
}
```

### Circuit Breaker Interceptor

```kotlin
class CircuitBreakerInterceptor(
    private val failureThreshold: Int = 5,
    private val timeout: Duration = 60.seconds
) : Interceptor<StoreKey, Any> {
    private var state: CircuitState = CircuitState.Closed
    private var failureCount = 0

    override suspend fun intercept(
        chain: InterceptorChain<StoreKey, Any>,
        request: StoreRequest<StoreKey>
    ): StoreResponse<Any> {
        when (state) {
            is CircuitState.Open -> {
                throw CircuitBreakerOpenException("Circuit breaker is open")
            }
            is CircuitState.HalfOpen, CircuitState.Closed -> {
                return try {
                    val response = chain.proceed(request)
                    onSuccess()
                    response
                } catch (e: Exception) {
                    onFailure()
                    throw e
                }
            }
        }
    }

    private fun onSuccess() {
        failureCount = 0
        state = CircuitState.Closed
    }

    private fun onFailure() {
        failureCount++
        if (failureCount >= failureThreshold) {
            state = CircuitState.Open
        }
    }
}
```

## 🏗️ Architecture

### Module Dependencies

```
interceptors
├── core (API dependency)
│   └── Store interface
├── kotlinx-coroutines-core
└── Zero additional dependencies
```

### Package Structure

```
dev.mattramotar.storex.interceptors
├── Interceptor.kt           # Main interface (placeholder)
├── InterceptorChain.kt      # Chain pattern (planned)
└── builtin/                 # Built-in interceptors (planned)
    ├── LoggingInterceptor.kt
    ├── MetricsInterceptor.kt
    ├── AuthInterceptor.kt
    ├── RetryInterceptor.kt
    └── CircuitBreakerInterceptor.kt
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (required dependency)
- **`:telemetry`** - Observability and metrics (integrates with interceptors)
- **`:resilience`** - Retry, circuit breaking (can be used in interceptors)
- **`:bundle-graphql`** - Pre-configured bundle (includes `:interceptors`)
- **`:bundle-rest`** - Pre-configured bundle (includes `:interceptors`)

## 📖 Documentation

For detailed information, see:

- [Core Module](../core/README.md) - Base store functionality
- [ARCHITECTURE.md](../ARCHITECTURE.md) - Interceptor architecture
- [API Documentation](../docs/api/interceptors/) - Complete API reference

## 💡 Planned Best Practices

1. **Order matters** - Add interceptors in execution order (auth before logging)
2. **Keep interceptors focused** - One concern per interceptor
3. **Handle errors gracefully** - Don't let interceptors crash the store
4. **Use suspend functions** - For async operations (network, DB)
5. **Avoid state** - Make interceptors stateless when possible
6. **Test interceptors** - Unit test each interceptor independently
7. **Document side effects** - Be clear about what interceptors do

## 📊 Roadmap

### v1.1 (Planned)
- [ ] Core `Interceptor` interface implementation
- [ ] `InterceptorChain` implementation
- [ ] Built-in `LoggingInterceptor`
- [ ] Built-in `MetricsInterceptor`

### v1.2 (Planned)
- [ ] Built-in `AuthInterceptor`
- [ ] Built-in `RetryInterceptor`
- [ ] Built-in `CircuitBreakerInterceptor`
- [ ] Built-in `CacheInterceptor`

### v2.0 (Future)
- [ ] Interceptor composition
- [ ] Conditional interceptors
- [ ] Async interceptor chains
- [ ] Request/response transformation

## 🤝 Contributing

This module is a placeholder and contributions are welcome! If you'd like to help implement the interceptor system, please:

1. Review the [ARCHITECTURE.md](../ARCHITECTURE.md) for design patterns
2. Check existing issues/discussions on GitHub
3. Propose your implementation approach
4. Submit a PR with tests and documentation

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
