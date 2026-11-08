package com.peppeosmio.lockate.service.location

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationFix
import com.peppeosmio.lockate.domain.UserActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PositionProviderTest {

    private fun fix(lat: Double, accuracy: Float, at: Long) =
        LocationFix(Coordinates(lat, 0.0), accuracy, at)

    @Test
    fun `an accurate fix replaces the remembered one`() {
        val old = fix(1.0, 5f, 0)
        val new = fix(2.0, 25f, 1000)
        assertEquals(new, pickGoodFix(old, new))
    }

    @Test
    fun `an inaccurate fix doesn't replace a recent better one`() {
        val old = fix(1.0, 50f, 0)
        assertEquals(old, pickGoodFix(old, fix(2.0, 80f, 1000)))
    }

    @Test
    fun `an inaccurate fix replaces a worse one`() {
        val new = fix(2.0, 40f, 1000)
        assertEquals(new, pickGoodFix(fix(1.0, 80f, 0), new))
    }

    @Test
    fun `an inaccurate fix replaces a stale one`() {
        val new = fix(2.0, 80f, 61_000)
        assertEquals(new, pickGoodFix(fix(1.0, 5f, 0), new))
    }

    private class Fakes {
        val gps = MutableSharedFlow<LocationFix>()
        val passive = MutableSharedFlow<LocationFix>()
        val activity = MutableStateFlow(UserActivity.Unknown)
        var gpsCollectors = 0

        fun provider(scope: TestScope) =
            PositionProvider(
                gpsFixes = {
                    gps.onStart { gpsCollectors++ }.onCompletion { gpsCollectors-- }
                },
                passiveFixes = { passive },
                activities = { activity },
                now = { scope.testScheduler.currentTime },
                stillJitterMillis = { 0 },
            )
    }

    private fun TestScope.collect(flow: Flow<Position>): MutableList<Position> {
        val positions = mutableListOf<Position>()
        backgroundScope.launch { flow.collect { positions += it } }
        runCurrent()
        return positions
    }

    @Test
    fun `the still tick falls on the next minute plus the jitter`() {
        assertEquals(59_000L + 500, millisToNextStillTick(61_000, 500))
        assertEquals(60_000L, millisToNextStillTick(120_000, 0))
    }

    @Test
    fun `the still tick skips a minute that is too close`() {
        assertEquals(61_000L, millisToNextStillTick(59_000, 0))
    }

    @Test
    fun `while moving every GPS fix is sent`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())

        fakes.gps.emit(fix(1.0, 5f, 0))
        fakes.gps.emit(fix(2.0, 80f, 0))
        runCurrent()

        assertEquals(listOf(1.0, 2.0), positions.map { it.fix.coordinates.latitude })
        assertTrue(positions.all { it.isFresh })
    }

    @Test
    fun `while still the GPS is off and the last good fix is repeated`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        fakes.gps.emit(fix(2.0, 80f, 0))
        runCurrent()

        fakes.activity.value = UserActivity.Still
        runCurrent()
        advanceTimeBy(STILL_INTERVAL_MILLIS * 3)
        runCurrent()

        assertEquals(0, fakes.gpsCollectors)
        val still = positions.drop(2)
        assertEquals(4, still.size)
        assertTrue(still.all { !it.isFresh && it.fix.coordinates.latitude == 1.0 })
        assertTrue(still.all { it.activity == UserActivity.Still })
    }

    @Test
    fun `still without an accurate fix keeps the GPS on until one arrives`() = runTest {
        val fakes = Fakes()
        fakes.activity.value = UserActivity.Still
        val positions = collect(fakes.provider(this).positions())

        fakes.gps.emit(fix(1.0, 80f, 0))
        runCurrent()
        assertEquals(1, fakes.gpsCollectors)

        fakes.gps.emit(fix(2.0, 5f, 0))
        runCurrent()

        assertEquals(0, fakes.gpsCollectors)
        assertEquals(listOf(1.0, 2.0, 2.0), positions.map { it.fix.coordinates.latitude })
    }

    @Test
    fun `still without an accurate fix releases the GPS after a while and repeats the best one`() =
        runTest {
            val fakes = Fakes()
            fakes.activity.value = UserActivity.Still
            val positions = collect(fakes.provider(this).positions())

            fakes.gps.emit(fix(1.0, 60f, currentTime))
            runCurrent()
            advanceTimeBy(STILL_ACQUIRE_MILLIS / 2)
            fakes.gps.emit(fix(2.0, 90f, currentTime))
            runCurrent()
            assertEquals(1, fakes.gpsCollectors)

            advanceTimeBy(STILL_ACQUIRE_MILLIS)
            fakes.gps.emit(fix(3.0, 70f, currentTime))
            runCurrent()
            advanceTimeBy(STILL_INTERVAL_MILLIS)
            runCurrent()

            assertEquals(0, fakes.gpsCollectors)
            val repeated = positions.last()
            assertFalse(repeated.isFresh)
            assertEquals(1.0, repeated.fix.coordinates.latitude, 0.0)
        }

    @Test
    fun `still with an old accurate fix keeps the GPS on`() = runTest {
        val fakes = Fakes()
        collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        runCurrent()

        advanceTimeBy(STALE_FIX_MILLIS + 1)
        fakes.activity.value = UserActivity.Still
        runCurrent()

        assertEquals(1, fakes.gpsCollectors)
    }

    @Test
    fun `while still the fixes of the other collectors are used`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        runCurrent()
        fakes.activity.value = UserActivity.Still
        runCurrent()

        fakes.passive.emit(fix(3.0, 5f, 1000))
        advanceTimeBy(STILL_INTERVAL_MILLIS * 2)
        runCurrent()

        assertEquals(3.0, positions.last().fix.coordinates.latitude, 0.0)
    }

    @Test
    fun `after being still the last good fix is held until an accurate one`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        runCurrent()
        fakes.activity.value = UserActivity.Still
        runCurrent()
        fakes.activity.value = UserActivity.OnFoot
        runCurrent()
        positions.clear()

        fakes.gps.emit(fix(2.0, 80f, 0))
        fakes.gps.emit(fix(3.0, 10f, 0))
        fakes.gps.emit(fix(4.0, 80f, 0))
        runCurrent()

        assertEquals(listOf(1.0, 3.0, 4.0), positions.map { it.fix.coordinates.latitude })
        assertEquals(listOf(false, true, true), positions.map { it.isFresh })
        assertTrue(positions.all { it.activity == UserActivity.OnFoot })
    }

    @Test
    fun `while still the last good fix is sent once a minute`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, currentTime))
        runCurrent()
        fakes.activity.value = UserActivity.Still
        runCurrent()
        positions.clear()

        advanceTimeBy(STILL_INTERVAL_MILLIS * 5 - 1)
        runCurrent()

        assertEquals(4, positions.size)
    }

    @Test
    fun `after being still the activity change is sent at once and the fix is not repeated`() =
        runTest {
            val fakes = Fakes()
            val positions = collect(fakes.provider(this).positions())
            fakes.gps.emit(fix(1.0, 5f, 0))
            runCurrent()
            fakes.activity.value = UserActivity.Still
            runCurrent()
            fakes.activity.value = UserActivity.OnFoot
            runCurrent()
            positions.clear()

            advanceTimeBy(STILL_INTERVAL_MILLIS * 2)
            runCurrent()
            assertTrue(positions.isEmpty())

            fakes.gps.emit(fix(2.0, 5f, 0))
            runCurrent()
            assertEquals(listOf(2.0), positions.map { it.fix.coordinates.latitude })
        }

    @Test
    fun `stopping is sent at once`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        runCurrent()

        fakes.activity.value = UserActivity.Still
        runCurrent()

        assertEquals(UserActivity.Still, positions.last().activity)
    }

    @Test
    fun `switching between moving activities sends the last fix at once`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        runCurrent()
        fakes.activity.value = UserActivity.InVehicle
        runCurrent()

        assertEquals(1.0, positions.last().fix.coordinates.latitude, 0.0)
        assertEquals(UserActivity.InVehicle, positions.last().activity)
    }

    @Test
    fun `without a recent fix nothing is sent before the GPS answers`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        runCurrent()
        advanceTimeBy(STALE_FIX_MILLIS + 1)
        fakes.activity.value = UserActivity.InVehicle
        runCurrent()

        assertEquals(1, positions.size)
    }

    @Test
    fun `the warm up ends without an accurate fix`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        runCurrent()
        fakes.activity.value = UserActivity.Still
        runCurrent()
        fakes.activity.value = UserActivity.InVehicle
        runCurrent()
        positions.clear()

        advanceTimeBy(WARM_UP_MILLIS)
        fakes.gps.emit(fix(2.0, 80f, 0))
        runCurrent()

        assertEquals(2.0, positions.last().fix.coordinates.latitude, 0.0)
        assertTrue(positions.last().isFresh)
    }

    @Test
    fun `switching between moving activities doesn't warm up`() = runTest {
        val fakes = Fakes()
        val positions = collect(fakes.provider(this).positions())
        fakes.gps.emit(fix(1.0, 5f, 0))
        runCurrent()
        fakes.activity.value = UserActivity.InVehicle
        runCurrent()
        positions.clear()

        fakes.gps.emit(fix(2.0, 80f, 0))
        runCurrent()

        assertTrue(positions.last().isFresh)
        assertEquals(2.0, positions.last().fix.coordinates.latitude, 0.0)
    }
}
