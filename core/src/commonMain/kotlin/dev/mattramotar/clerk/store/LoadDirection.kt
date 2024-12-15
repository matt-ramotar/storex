package dev.mattramotar.clerk.store

/**
 * Indicates the direction of pagination:
 * - [Prepend]: Loading data before the current set (towards the beginning).
 * - [Append]: Loading data after the current set (towards the end).
 */
enum class LoadDirection {
    Prepend,
    Append
}