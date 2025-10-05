# StoreX Serialization (Kotlinx)

**Kotlinx Serialization integration for StoreX converters**

The `:serialization-kotlinx` module provides seamless integration between StoreX and Kotlinx Serialization, enabling automatic conversion between network JSON, database storage, and domain models using Kotlin's built-in serialization.

> **Status**: 🚧 **Placeholder Implementation** - Full implementation planned for future release

## 📦 What's Included

This module will provide:

- **`SerializationConverter<Key, Domain>`** - Automatic converter using Kotlinx Serialization
- **JSON Support** - Serialize/deserialize JSON responses
- **Custom Formats** - Support for ProtoBuf, CBOR, and other formats
- **Type-Safe Conversions** - Compile-time checked serialization
- **Polymorphic Support** - Handle sealed classes and interfaces
- **Custom Serializers** - For complex types

## 🎯 When to Use

Use this module when:

- Your API returns **JSON** (REST, GraphQL)
- You want **automatic conversion** without manual parsing
- You're using **Kotlinx Serialization** in your project
- You need **type-safe** serialization/deserialization
- You want to **reduce boilerplate** converter code

## 🚀 Planned Usage

```kotlin
import dev.mattramotar.storex.core.*
import dev.mattramotar.storex.serialization.*
import kotlinx.serialization.Serializable

// Define serializable models
@Serializable
data class UserResponse(
    val id: String,
    val name: String,
    val email: String
)

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String
)

// Create store with automatic serialization
val userStore = store<ByIdKey, User> {
    fetcher { key ->
        flow {
            // API returns JSON string
            val json = api.getUser(key.entity.id)
            emit(FetcherResult.Success(json))
        }
    }

    // Automatic conversion via kotlinx.serialization
    serializationConverter<UserResponse, User>(
        netToDbWrite = { response -> response },
        dbReadToDomain = { dbUser -> dbUser }
    )
}

// Even simpler when types are identical
val userStore2 = store<ByIdKey, User> {
    fetcher { key -> /* ... */ }

    // Single line!
    serializationConverter<User>()
}
```

## 📚 Planned Features

### JSON Deserialization

```kotlin
import kotlinx.serialization.json.Json

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

val store = store<ByIdKey, User> {
    fetcher { key ->
        flow {
            val jsonString = api.getUser(key.entity.id)
            // Automatic deserialization
            val user = json.decodeFromString<User>(jsonString)
            emit(FetcherResult.Success(user))
        }
    }

    serializationConverter<User>(json = json)
}
```

### Custom Serializers

```kotlin
@Serializable(with = InstantSerializer::class)
data class Event(
    val id: String,
    val timestamp: Instant,
    val data: String
)

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}
```

### Polymorphic Types

```kotlin
@Serializable
sealed class ApiResponse {
    @Serializable
    @SerialName("success")
    data class Success(val data: User) : ApiResponse()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ApiResponse()
}

val store = store<ByIdKey, User> {
    fetcher { key ->
        flow {
            val response: ApiResponse = api.getUser(key.entity.id)
            when (response) {
                is ApiResponse.Success -> emit(FetcherResult.Success(response.data))
                is ApiResponse.Error -> emit(FetcherResult.Error(ApiException(response.message)))
            }
        }
    }

    serializationConverter<ApiResponse.Success, User>(
        netToDbWrite = { success -> success.data },
        dbReadToDomain = { user -> user }
    )
}
```

## 🏗️ Architecture

### Module Dependencies

```
serialization-kotlinx
├── core (API dependency)
│   └── Converter interface
├── kotlinx-serialization-json
└── kotlinx-serialization-core
```

### Package Structure

```
dev.mattramotar.storex.serialization
├── SerializationConverter.kt    # Main converter (placeholder)
├── JsonConfig.kt                 # JSON configuration (planned)
└── serializers/                  # Custom serializers (planned)
    ├── InstantSerializer.kt
    ├── DurationSerializer.kt
    └── UuidSerializer.kt
```

## 🔗 Related Modules

- **`:core`** - Base Store functionality (required dependency)
- **`:ktor-client`** - Ktor HTTP client integration (uses serialization)
- **`:bundle-rest`** - Pre-configured bundle (includes serialization)

## 💡 Planned Best Practices

1. **Use @Serializable** on all data classes
2. **Configure JSON settings** for flexibility (ignoreUnknownKeys, etc.)
3. **Custom serializers** for platform-specific types (Instant, UUID)
4. **Handle polymorphism** with sealed classes
5. **Test serialization** separately from store logic
6. **Use format-agnostic** serialization when possible

## 📊 Roadmap

### v1.1 (Planned)
- [ ] `SerializationConverter` implementation
- [ ] JSON support with kotlinx.serialization
- [ ] Common custom serializers (Instant, Duration, UUID)
- [ ] Error handling for malformed JSON

### v1.2 (Planned)
- [ ] ProtoBuf support
- [ ] CBOR support
- [ ] Custom format support
- [ ] Streaming deserialization

### v2.0 (Future)
- [ ] Schema evolution
- [ ] Migration support
- [ ] Compression support

## 📄 License

Apache 2.0 - See [LICENSE](../LICENSE) for details.
