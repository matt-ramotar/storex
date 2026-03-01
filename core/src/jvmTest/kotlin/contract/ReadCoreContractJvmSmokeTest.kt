package dev.mattramotar.storex.core.contract

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ReadCoreContractJvmSmokeTest {
    @Test
    fun smoke() = runTest {
        runReadCoreContractSmokeScenario()
    }
}
