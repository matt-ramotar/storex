package dev.mattramotar.storex.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val Dispatchers.io: CoroutineDispatcher
    get() = Dispatchers.IO