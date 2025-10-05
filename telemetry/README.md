# StoreX Telemetry

**Observability, metrics, and monitoring for StoreX**

The `:telemetry` module provides comprehensive observability for StoreX applications, including metrics collection, distributed tracing, logging integration, and real-time performance monitoring.

> **Status**: 🚧 **Placeholder Implementation** - Full implementation planned for future release

## 📦 What's Included

This module will provide:

- **`Telemetry`** - Metrics and tracing API
- **Metrics Collection** - Cache hits/misses, latencies, error rates
- **Distributed Tracing** - Request spans across store layers
- **Performance Monitoring** - Real-time dashboards
- **Health Checks** - Store health and diagnostics
- **Export Adapters** - Prometheus, OpenTelemetry, DataDog, etc.

## 🎯 When to Use

Use this module for:

- **Production monitoring** of StoreX performance
- **Cache hit rate tracking** for optimization
- **Error rate monitoring** and alerting
- **Latency tracking** across store layers
- **Distributed tracing** in microservices
- **Performance debugging** and profiling

## 🚀 Planned Usage

```kotlin
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.telemetry.*

// Configure telemetry
val telemetry = Telemetry {
    // Metrics backend
    metrics {
        backend = PrometheusBackend(port = 9090)
        collectInterval = 10.seconds
    }

    // Distributed tracing
    tracing {
        backend = OpenTelemetryBackend(endpoint = "http://jaeger:14268")
        sampleRate = 0.1  // 10% sampling
    }

    // Logging
    logging {
        level = LogLevel.INFO
        format = LogFormat.JSON
    }
}

// Create store with telemetry
val userStore = store<ByIdKey, User> {
    fetcher { key ->
        flow {
            val user = api.getUser(key.entity.id)
            emit(FetcherResult.Success(user))
        }
    }

    // Enable telemetry
    telemetry(telemetry)
}

// Query metrics
val metrics = telemetry.metrics()
println("Cache hit rate: ${metrics.cacheHitRate()}")
println("Avg latency: ${metrics.avgLatency()}")
println("Error rate: ${metrics.errorRate()}")
```

## 📚 Planned Features

### Metrics Collection

```kotlin
interface StoreMetrics {
    // Cache metrics
    fun cacheHitRate(): Double
    fun cacheHits(): Long
    fun cacheMisses(): Long
    fun cacheSize(): Int

    // Latency metrics
    fun avgLatency(): Duration
    fun p50Latency(): Duration
    fun p95Latency(): Duration
    fun p99Latency(): Duration

    // Error metrics
    fun errorRate(): Double
    fun errorCount(): Long
    fun errors(): Map<String, Long>  // By error type

    // Operation metrics
    fun fetchCount(): Long
    fun updateCount(): Long
    fun invalidationCount(): Long
}
```

### Distributed Tracing

```kotlin
// Automatic span creation
store.get(key)
// Creates span:
//   store.get
//   ├─ cache.lookup
//   ├─ sot.read
//   ├─ fetcher.fetch
//   │  └─ http.request
//   └─ sot.write

// Custom spans
telemetry.trace("custom-operation") {
    // Your code
}
```

### Real-Time Dashboards

```kotlin
// Expose metrics endpoint
val server = TelemetryServer(port = 8080)

// Dashboard at http://localhost:8080/dashboard
// - Cache hit rate over time
// - Latency percentiles
// - Error rate by type
// - Active operations
// - Memory usage
```

### Health Checks

```kotlin
val health = telemetry.healthCheck()

when {
    health.cacheHitRate < 0.5 -> log.warn("Low cache hit rate")
    health.errorRate > 0.1 -> log.error("High error rate")
    health.avgLatency > 1.seconds -> log.warn("High latency")
}
```

### Export Adapters

```kotlin
// Prometheus
telemetry {
    metrics {
        backend = PrometheusBackend {
            port = 9090
            path = "/metrics"
        }
    }
}

// OpenTelemetry
telemetry {
    tracing {
        backend = OpenTelemetryBackend {
            endpoint = "http://jaeger:14268"
            serviceName = "my-app"
        }
    }
}

// DataDog
telemetry {
    metrics {
        backend = DataDogBackend {
            apiKey = env("DD_API_KEY")
            tags = mapOf("env" to "prod", "service" to "api")
        }
    }
}

// Custom backend
telemetry {
    metrics {
        backend = CustomBackend { metrics ->
            // Send to your backend
        }
    }
}
```

## 🏗️ Architecture

### Module Dependencies

```
telemetry
├── core (API dependency)
│   └── Store interface
├── kotlinx-coroutines-core
├── kotlinx-datetime
└── Optional backends:
    ├── prometheus-client (optional)
    ├── opentelemetry-sdk (optional)
    └── micrometer-core (optional)
```

### Package Structure

```
dev.mattramotar.storex.telemetry
├── Telemetry.kt                  # Main interface (placeholder)
├── StoreMetrics.kt               # Metrics API (planned)
├── StoreTracing.kt               # Tracing API (planned)
├── HealthCheck.kt                # Health checks (planned)
├── backends/                     # Export backends (planned)
│   ├── PrometheusBackend.kt
│   ├── OpenTelemetryBackend.kt
│   ├── DataDogBackend.kt
│   └── MicrometerBackend.kt
└── server/                       # Dashboard server (planned)
    └── TelemetryServer.kt
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (monitored by this module)
- **`:interceptors`** - Interception (can integrate with telemetry)
- **`:testing`** - Test utilities (can mock telemetry)

## 💡 Planned Best Practices

1. **Enable in production** - Essential for monitoring
2. **Use sampling for traces** - Don't trace 100% in prod
3. **Set up alerts** - For cache hit rate, error rate, latency
4. **Monitor memory** - Especially with large caches
5. **Export to centralized system** - Prometheus, DataDog, etc.
6. **Dashboard key metrics** - Cache hits, latencies, errors
7. **Correlate with business metrics** - Not just technical metrics

## 📊 Roadmap

### v1.1 (Planned)
- [ ] Core `Telemetry` interface
- [ ] Basic metrics collection
- [ ] Prometheus export
- [ ] Simple health checks

### v1.2 (Planned)
- [ ] Distributed tracing with OpenTelemetry
- [ ] Real-time dashboard
- [ ] DataDog integration
- [ ] Advanced metrics (histograms, gauges)

### v2.0 (Future)
- [ ] Alerting system
- [ ] Anomaly detection
- [ ] Performance profiling
- [ ] Cost tracking

## 📊 Example Metrics

```
# StoreX Metrics Example

# Cache metrics
storex_cache_hit_rate{store="user_store"} 0.85
storex_cache_hits_total{store="user_store"} 1250
storex_cache_misses_total{store="user_store"} 220

# Latency metrics (ms)
storex_latency_avg{store="user_store"} 45
storex_latency_p50{store="user_store"} 30
storex_latency_p95{store="user_store"} 120
storex_latency_p99{store="user_store"} 250

# Error metrics
storex_error_rate{store="user_store"} 0.02
storex_errors_total{store="user_store",type="network"} 15
storex_errors_total{store="user_store",type="timeout"} 5

# Operation metrics
storex_fetch_count_total{store="user_store"} 450
storex_update_count_total{store="user_store"} 120
storex_invalidation_count_total{store="user_store"} 30
```

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
