package com.peppeosmio.lockate.platform_service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AwaitSignalTest {

    @Test
    fun `returns immediately without collecting the signal when condition already holds`() = runTest {
        var collected = false
        val signal = flow<Unit> { collected = true }

        awaitSignal(signal) { true }

        assertFalse("signal must not be collected when the condition is already true", collected)
    }

    @Test
    fun `does not return while the condition stays false`() = runTest {
        val signal = MutableSharedFlow<Unit>()

        val job = launch { awaitSignal(signal) { false } }
        runCurrent()
        signal.emit(Unit)
        signal.emit(Unit)
        runCurrent()

        assertTrue("must keep waiting while the condition is false", job.isActive)
        job.cancel()
    }

    @Test
    fun `returns on the first emission after which the condition holds`() = runTest {
        val signal = MutableSharedFlow<Unit>()
        var checks = 0
        var enabledAfter = 2

        val job = launch {
            awaitSignal(signal) {
                checks += 1
                // becomes true only from the 2nd signal-driven check onward
                checks > enabledAfter
            }
        }
        runCurrent()
        assertTrue(job.isActive) // initial check (1) was false

        signal.emit(Unit) // check 2 -> false
        runCurrent()
        assertTrue(job.isActive)

        signal.emit(Unit) // check 3 -> true
        runCurrent()
        assertFalse("should complete once the condition holds", job.isActive)
        assertEquals(3, checks)
    }

    @Test
    fun `condition true on the initial check ignores an empty signal`() = runTest {
        // emptyFlow would complete without emitting; if awaitSignal tried to collect it and the
        // condition were false it would return prematurely - here it must not even collect.
        awaitSignal(emptyFlow()) { true }
    }
}
