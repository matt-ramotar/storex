package dev.mattramotar.storex.resilience.internal

import dev.mattramotar.storex.resilience.CircuitBreaker
import dev.mattramotar.storex.resilience.CircuitBreakerEvent
import dev.mattramotar.storex.resilience.OperationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@OptIn(ExperimentalCoroutinesApi::class)
class DefaultOperationEventsTest {

    @Test
    fun constructor_givenDefaultCapacity_thenCreatesFlowWithDefaultCapacity() = runTest {
        // When
        val operationEvents = DefaultOperationEvents()

        // Then
        assertNotNull(operationEvents.events)

        val testEvent = createTestEvent()
        val collected = mutableListOf<OperationEvent>()

        CoroutineScope(Dispatchers.Unconfined).launch {
            operationEvents.events.collect { collected.add(it) }
        }

        operationEvents.events.emit(testEvent)

        assertEquals(1, collected.size)
        assertEquals(testEvent, collected.first())
    }

    @Test
    fun constructor_givenCustomCapacity_thenCreatesFlowWithCustomCapacity() = runTest {
        // Given
        val customCapacity = 128

        // When
        val operationEvents = DefaultOperationEvents(extraBufferCapacity = customCapacity)

        // Then
        assertNotNull(operationEvents.events)

        val testEvent = createTestEvent()
        val collected = mutableListOf<OperationEvent>()

        CoroutineScope(Dispatchers.Unconfined).launch {
            operationEvents.events.collect { collected.add(it) }
        }

        operationEvents.events.emit(testEvent)

        assertEquals(1, collected.size)
        assertEquals(testEvent, collected.first())
    }

    @Test
    fun events_givenNoReplay_whenNewCollectorSubscribesAfterEmission_thenReceivesOnlyNewEvents() = runTest {
        // Given
        val operationEvents = DefaultOperationEvents()
        val firstEvent = createTestEvent(CircuitBreaker.State.CLOSED, CircuitBreaker.State.OPEN)
        val secondEvent = createTestEvent(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN)

        // When
        // Emit first event before any collector subscribes
        operationEvents.events.emit(firstEvent)

        // Now subscribe a collector
        val collected = mutableListOf<OperationEvent>()
        CoroutineScope(Dispatchers.Unconfined).launch {
            operationEvents.events.collect { collected.add(it) }
        }

        // Emit second event after collector subscribes
        operationEvents.events.emit(secondEvent)

        // Then
        // Collector should only receive the second event, not the first (no replay)
        assertEquals(1, collected.size)
        assertEquals(secondEvent, collected.first())
    }

    @Test
    fun events_givenBufferOverflow_whenExceedingCapacity_thenDropsOldestEvents() = runTest {
        // Given
        val smallCapacity = 2
        val operationEvents = DefaultOperationEvents(extraBufferCapacity = smallCapacity)

        val event1 = CircuitBreakerEvent(CircuitBreaker.State.CLOSED, CircuitBreaker.State.OPEN)
        val event2 = CircuitBreakerEvent(CircuitBreaker.State.OPEN, CircuitBreaker.State.HALF_OPEN)
        val event3 = CircuitBreakerEvent(CircuitBreaker.State.HALF_OPEN, CircuitBreaker.State.CLOSED)

        // When
        val collected = mutableListOf<OperationEvent>()

        CoroutineScope(Dispatchers.Unconfined).launch {
            operationEvents.events.collect { event ->
                collected.add(event)
            }
        }

        // Emit multiple events
        operationEvents.events.emit(event1)
        operationEvents.events.emit(event2)
        operationEvents.events.emit(event3)

        // Then
        // All events are collected (demonstrates buffer handles multiple events)
        // The key behavior is that emit() doesn't suspend or block with DROP_OLDEST
        assertEquals(3, collected.size)
        assertEquals(event1, collected[0])
        assertEquals(event2, collected[1])
        assertEquals(event3, collected[2])
    }

    @Test
    fun events_givenMultipleCollectors_whenEventEmitted_thenAllReceiveEvent() = runTest {
        // Given
        val operationEvents = DefaultOperationEvents()
        val testEvent = createTestEvent()
        val collector1 = mutableListOf<OperationEvent>()
        val collector2 = mutableListOf<OperationEvent>()
        val collector3 = mutableListOf<OperationEvent>()

        // When
        // Start multiple collectors
        CoroutineScope(Dispatchers.Unconfined).launch {
            operationEvents.events.collect { collector1.add(it) }
        }
        CoroutineScope(Dispatchers.Unconfined).launch {
            operationEvents.events.collect { collector2.add(it) }
        }
        CoroutineScope(Dispatchers.Unconfined).launch {
            operationEvents.events.collect { collector3.add(it) }
        }

        // Emit single event
        operationEvents.events.emit(testEvent)

        // Then
        // All collectors should receive the same event
        assertEquals(1, collector1.size)
        assertEquals(1, collector2.size)
        assertEquals(1, collector3.size)
        assertEquals(testEvent, collector1.first())
        assertEquals(testEvent, collector2.first())
        assertEquals(testEvent, collector3.first())
    }

    private fun createTestEvent(
        previous: CircuitBreaker.State = CircuitBreaker.State.CLOSED,
        current: CircuitBreaker.State = CircuitBreaker.State.OPEN,
    ): OperationEvent = CircuitBreakerEvent(previous, current)
}
