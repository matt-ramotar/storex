package dev.mattramotar.storex.pager.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val Dispatchers.io: CoroutineDispatcher
    get() = IO