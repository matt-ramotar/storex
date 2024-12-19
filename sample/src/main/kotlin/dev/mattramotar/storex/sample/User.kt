package dev.mattramotar.storex.sample

import dev.mattramotar.storex.repository.runtime.Model

sealed class User : Model<User.Key, User.Properties, User.Edges> {
    data class Key(
        val id: String
    ) : Model.Key

    data class Properties(
        val name: String
    ) : Model.Properties

    data object Edges : Model.Edges

    data class Node(
        override val key: Key,
        override val properties: Properties
    ) : Model.Node<Key, Properties, Edges>

    data class Composite(
        override val edges: Edges,
        override val node: Model.Node<Key, Properties, Edges>
    ) : Model.Composite<Key, Properties, Edges>
}