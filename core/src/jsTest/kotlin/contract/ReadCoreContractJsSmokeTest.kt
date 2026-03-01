package dev.mattramotar.storex.core.contract

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ReadCoreContractJsSmokeTest {
    @Test
    fun smoke() = runTest {
        runReadCoreContractSmokeScenario()
    }
}
