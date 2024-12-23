package dev.mattramotar.storex.sample

import dev.mattramotar.storex.mutablestore.core.api.MutableStore
import dev.mattramotar.storex.store.core.api.Store

class UserRepositoryFactory {
    fun create(
        store: Store<User.Key, User.Node>,
        compositeStore: Store<User.Key, User.Composite>,
        mutableStore: MutableStore<User.Key, User.Properties, User.Node, CustomError>
    ): UserRepository {
        return UserRepositoryBuilder(
            store,
            compositeStore,
            mutableStore
        ) { CustomError.Message(it.message.orEmpty()) }
            .build()
    }
}


suspend fun sample(userRepository: UserRepository) {
    val user = userRepository.findOne(User.Key(""))
    val createdUser = userRepository.createOne(
        key = User.Key(""),
        properties = User.Properties("")
    )

    val compositeUser = userRepository.findOneComposite(User.Key(""))
    val updatedUser = userRepository.updateOne(
        User.Key(""),
        user.getOrThrow()
    )
}