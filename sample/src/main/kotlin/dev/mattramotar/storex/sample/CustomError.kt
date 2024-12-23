package dev.mattramotar.storex.sample

sealed class CustomError {
    data class Message(val error: String): CustomError()
}