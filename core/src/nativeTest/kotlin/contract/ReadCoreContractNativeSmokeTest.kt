package dev.mattramotar.storex.core.contract

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ReadCoreContractNativeSmokeTest {
    @Test
    fun smoke() = runTest {
        runReadCoreContractSmokeScenario()
    }
}
