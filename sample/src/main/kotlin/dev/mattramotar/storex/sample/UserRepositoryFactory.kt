package dev.mattramotar.storex.sample

import dev.mattramotar.storex.repository.runtime.DataSource
import dev.mattramotar.storex.repository.runtime.DataSources
import dev.mattramotar.storex.repository.runtime.Result
import dev.mattramotar.storex.repository.runtime.operations.query.FindAllOperation
import dev.mattramotar.storex.repository.runtime.operations.query.FindOneOperation

class UserRepositoryFactory {
    fun create(): UserRepository {
        return UserRepositoryBuilder()
            .withFindOneOperation(findOneOperation())
            .withFindAllOperation(findAllOperation())
            .build()
    }

    private fun findOneOperation(): FindOneOperation<User.Key, User.Node, Throwable> {
        TODO()
    }

    private fun findAllOperation(): FindAllOperation<User.Node, Throwable> {
        TODO()
    }
}


suspend fun sample(userRepository: UserRepository) {
    val user: Result<User.Node, Throwable> =
        userRepository.findOne(
            key = User.Key("1"),
            dataSources = DataSources(listOf(DataSource.CACHE, DataSource.DISK))
        )

    val allUsers: Result<List<User.Node>, Throwable> = userRepository.findAll(
        dataSources = DataSources(listOf(DataSource.REMOTE))
    )
}