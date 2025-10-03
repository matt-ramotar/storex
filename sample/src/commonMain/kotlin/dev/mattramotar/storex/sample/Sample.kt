package dev.mattramotar.storex.sample

import dev.mattramotar.storex.normalization.EntityId
import dev.mattramotar.storex.normalization.EntityRef
import dev.mattramotar.storex.normalization.EntityRefId
import dev.mattramotar.storex.normalization.EntityRefs
import dev.mattramotar.storex.normalization.Normalizable

@Normalizable
data class User(
    @EntityId val id: String,
    val name: String,
    @EntityRefs("Message") val messages: List<Message>,
)


@Normalizable
data class Message(
    @EntityId val id: String,
    val content: String,
    @EntityRef("User") val user: User
)
