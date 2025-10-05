package dev.mattramotar.storex.core.utils

import dev.mattramotar.storex.core.ByIdKey
import dev.mattramotar.storex.core.EntityId
import dev.mattramotar.storex.core.QueryKey
import dev.mattramotar.storex.core.StoreKey
import dev.mattramotar.storex.core.StoreNamespace

/**
 * Test data fixtures and utilities for core module tests.
 */

// Test namespaces
val TEST_NAMESPACE = StoreNamespace("test")
val USERS_NAMESPACE = StoreNamespace("users")
val ARTICLES_NAMESPACE = StoreNamespace("articles")

// Test entities
val USER_ENTITY_1 = EntityId("User", "user-1")
val USER_ENTITY_2 = EntityId("User", "user-2")
val ARTICLE_ENTITY_1 = EntityId("Article", "article-1")

// Test keys
val TEST_KEY_1: StoreKey = ByIdKey(TEST_NAMESPACE, USER_ENTITY_1)
val TEST_KEY_2: StoreKey = ByIdKey(TEST_NAMESPACE, USER_ENTITY_2)
val ARTICLE_KEY_1: StoreKey = ByIdKey(ARTICLES_NAMESPACE, ARTICLE_ENTITY_1)

val QUERY_KEY_1: StoreKey = QueryKey(
    namespace = TEST_NAMESPACE,
    query = mapOf("page" to "1", "limit" to "10")
)

val QUERY_KEY_2: StoreKey = QueryKey(
    namespace = TEST_NAMESPACE,
    query = mapOf("page" to "2", "limit" to "10")
)

// Test domain models
data class TestUser(
    val id: String,
    val name: String,
    val email: String
)

data class TestArticle(
    val id: String,
    val title: String,
    val content: String
)

// Test fixtures
val TEST_USER_1 = TestUser("user-1", "Alice", "alice@example.com")
val TEST_USER_2 = TestUser("user-2", "Bob", "bob@example.com")
val TEST_ARTICLE_1 = TestArticle("article-1", "Test Article", "Test content")

// Test exceptions
class TestException(message: String = "Test exception") : Exception(message)
class TestNetworkException(message: String = "Network error") : Exception(message)
