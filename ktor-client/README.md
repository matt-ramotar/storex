# StoreX Ktor Client

**Ktor HTTP client integration for StoreX**

The `:ktor-client` module provides seamless integration between StoreX and Ktor HTTP Client, including pre-configured fetchers, automatic retry, request/response logging, and platform-optimized engines.

> **Status**: 🚧 **Placeholder Implementation** - Full implementation planned for future release

## 📦 What's Included

This module will provide:

- **`KtorFetcher`** - Pre-configured Ktor-based fetcher
- **Automatic Retry** - Exponential backoff for failed requests
- **Request Logging** - Built-in request/response logging
- **ETags Support** - Conditional requests with If-None-Match
- **Compression** - Automatic gzip/deflate support
- **Auth Integration** - Bearer tokens, OAuth, custom auth
- **Platform Engines** - Optimized for JVM, Native, JS

## 🎯 When to Use

Use this module for:

- **REST APIs** with Ktor Client
- **HTTP fetching** in StoreX
- **Automatic retry** and error handling
- **ETags** and conditional requests
- **Multiplatform** HTTP support

## 🚀 Planned Usage

```kotlin
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.ktor.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*

// Create Ktor client
val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(Logging) {
        level = LogLevel.INFO
    }
}

// Create store with Ktor fetcher
val userStore = store<ByIdKey, User> {
    // Simple fetcher
    ktorFetcher(httpClient) { key ->
        get("https://api.example.com/users/${key.entity.id}")
    }

    // Or with full control
    fetcher { key ->
        flow {
            try {
                val user: User = httpClient.get("https://api.example.com/users/${key.entity.id}")
                emit(FetcherResult.Success(user))
            } catch (e: Exception) {
                emit(FetcherResult.Error(e))
            }
        }
    }
}

// With ETags
val userStore2 = store<ByIdKey, User> {
    ktorFetcher(httpClient) { key, request ->
        val headers = buildMap {
            request.conditional?.ifNoneMatch?.let { etag ->
                put("If-None-Match", etag)
            }
        }

        get("https://api.example.com/users/${key.entity.id}") {
            headers.forEach { (k, v) -> header(k, v) }
        }
    }
}
```

## 📚 Planned Features

### Automatic Retry

```kotlin
val userStore = store<ByIdKey, User> {
    ktorFetcher(httpClient) { key ->
        get("https://api.example.com/users/${key.entity.id}")
    } withRetry {
        maxRetries = 3
        initialDelay = 1.seconds
        maxDelay = 30.seconds
        factor = 2.0  // Exponential backoff
        retryOnStatus = setOf(408, 429, 500, 502, 503, 504)
    }
}
```

### ETags and Conditional Requests

```kotlin
val userStore = store<ByIdKey, User> {
    ktorFetcher(httpClient) { key, request ->
        get("https://api.example.com/users/${key.entity.id}") {
            request.conditional?.let { conditional ->
                conditional.ifNoneMatch?.let { header("If-None-Match", it) }
                conditional.ifModifiedSince?.let { header("If-Modified-Since", it.toString()) }
            }
        }
    } withETagSupport()
}
```

### Authentication

```kotlin
// Bearer token
val userStore = store<ByIdKey, User> {
    ktorFetcher(httpClient) { key ->
        get("https://api.example.com/users/${key.entity.id}") {
            bearerAuth(tokenProvider.getToken())
        }
    }
}

// Custom auth
val userStore2 = store<ByIdKey, User> {
    ktorFetcher(httpClient) { key ->
        get("https://api.example.com/users/${key.entity.id}") {
            header("Authorization", "Custom ${authProvider.getAuth()}")
        }
    }
}
```

### Request Logging

```kotlin
val httpClient = HttpClient(CIO) {
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
        filter { request ->
            request.url.host.contains("api.example.com")
        }
    }
}

val userStore = store<ByIdKey, User> {
    ktorFetcher(httpClient) { key ->
        get("https://api.example.com/users/${key.entity.id}")
    }
}

// Output:
// REQUEST: https://api.example.com/users/123
// METHOD: HttpMethod(value=GET)
// RESPONSE: 200 OK
// BODY: {"id":"123","name":"Alice"}
```

### GraphQL Support

```kotlin
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?>? = null
)

val userStore = store<ByIdKey, User> {
    ktorFetcher(httpClient) { key ->
        post("https://api.example.com/graphql") {
            contentType(ContentType.Application.Json)
            setBody(GraphQLRequest(
                query = """
                    query GetUser(${"$"}id: ID!) {
                        user(id: ${"$"}id) {
                            id
                            name
                            email
                        }
                    }
                """.trimIndent(),
                variables = mapOf("id" to key.entity.id)
            ))
        }
    }
}
```

### Multipart Upload

```kotlin
val uploadStore = store<UploadKey, UploadResult> {
    ktorFetcher(httpClient) { key ->
        submitFormWithBinaryData(
            url = "https://api.example.com/upload",
            formData = formData {
                append("file", key.file.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=${key.file.name}")
                })
            }
        )
    }
}
```

## 🏗️ Architecture

### Module Dependencies

```
ktor-client
├── core (API dependency)
│   └── Fetcher interface
├── ktor-client-core
├── ktor-client-content-negotiation
├── ktor-client-logging
├── kotlinx-serialization-json
└── Platform engines:
    ├── ktor-client-cio (JVM)
    ├── ktor-client-darwin (iOS)
    ├── ktor-client-js (JS)
    └── ktor-client-okhttp (Android)
```

### Package Structure

```
dev.mattramotar.storex.ktor
├── KtorClientExtensions.kt      # Main extensions (placeholder)
├── fetcher/                      # Fetcher builders (planned)
│   ├── KtorFetcher.kt
│   └── KtorFetcherBuilder.kt
├── retry/                        # Retry logic (planned)
│   ├── RetryPolicy.kt
│   └── ExponentialBackoff.kt
├── etag/                         # ETag support (planned)
│   └── ETagHandler.kt
└── auth/                         # Auth helpers (planned)
    ├── BearerAuthFetcher.kt
    └── OAuthFetcher.kt
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (required dependency)
- **`:serialization-kotlinx`** - JSON serialization (recommended)
- **`:resilience`** - Advanced retry and circuit breaking
- **`:bundle-rest`** - Pre-configured bundle (includes `:ktor-client`)
- **`:bundle-graphql`** - Pre-configured bundle (includes `:ktor-client`)

## 💡 Planned Best Practices

1. **Use appropriate engine** - CIO for JVM, Darwin for iOS
2. **Enable compression** - Reduce bandwidth usage
3. **Configure timeouts** - Prevent hanging requests
4. **Use ETags** - Reduce unnecessary data transfer
5. **Log requests** - Debug and monitor API calls
6. **Handle errors** - Network failures, timeouts, HTTP errors
7. **Secure tokens** - Don't log sensitive headers

## 📊 Roadmap

### v1.1 (Planned)
- [ ] `ktorFetcher` builder
- [ ] Automatic retry with exponential backoff
- [ ] ETag support
- [ ] Platform-optimized engines

### v1.2 (Planned)
- [ ] GraphQL helpers
- [ ] Multipart upload support
- [ ] WebSocket support
- [ ] Advanced auth (OAuth, refresh tokens)

### v2.0 (Future)
- [ ] HTTP/3 support
- [ ] Server-sent events
- [ ] Request deduplication
- [ ] Circuit breaker integration

## 🔌 Platform Engines

### JVM
```kotlin
val client = HttpClient(CIO)  // Async, coroutine-based
```

### Android
```kotlin
val client = HttpClient(OkHttp)  // OkHttp integration
```

### iOS
```kotlin
val client = HttpClient(Darwin)  // Native URLSession
```

### JavaScript
```kotlin
val client = HttpClient(Js)  // Browser Fetch API
```

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
