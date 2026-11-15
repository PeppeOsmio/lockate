package com.peppeosmio.lockate.service.health

import com.peppeosmio.lockate.service.version.ApiCompatibility
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckTest {

    private class Fakes {
        var groups = listOf(HealthCheck.Group(connectionId = 1, anonymousGroupInternalId = 10))
        val compatibility = mutableMapOf<Long, ApiCompatibility?>()
        val verified = mutableListOf<Long>()
        val failures = mutableMapOf<Long, Int>()
        val incompatibleNotified = mutableMapOf<Long, Boolean>()
        val notifiedUnreachable = mutableListOf<Long>()
        val clearedUnreachable = mutableListOf<Long>()
        val notifiedIncompatible = mutableListOf<Pair<Long, ApiCompatibility>>()
        val clearedIncompatible = mutableListOf<Long>()
        var registrations = 0

        val check =
            HealthCheck(
                listGroups = { groups },
                compatibility = {
                    if (compatibility.containsKey(it)) compatibility[it]
                    else ApiCompatibility.Compatible
                },
                verify = { verified += it.anonymousGroupInternalId },
                registerPushes = { registrations++ },
                state =
                    object : HealthCheck.State {
                        override suspend fun failures(connectionId: Long) =
                            failures[connectionId] ?: 0

                        override suspend fun setFailures(connectionId: Long, count: Int) {
                            failures[connectionId] = count
                        }

                        override suspend fun incompatibleNotified(connectionId: Long) =
                            incompatibleNotified[connectionId] ?: false

                        override suspend fun setIncompatibleNotified(
                            connectionId: Long,
                            notified: Boolean,
                        ) {
                            incompatibleNotified[connectionId] = notified
                        }
                    },
                notifyUnreachable = { notifiedUnreachable += it },
                clearUnreachable = { clearedUnreachable += it },
                notifyIncompatible = { id, compatibility ->
                    notifiedIncompatible += id to compatibility
                },
                clearIncompatible = { clearedIncompatible += it },
            )
    }

    @Test
    fun `a reachable server resets its count, clears its notification and verifies its groups`() =
        runTest {
            val f = Fakes()
            f.failures[1] = 2

            f.check.run()

            assertEquals(0, f.failures[1])
            assertEquals(listOf(1L), f.clearedUnreachable)
            assertTrue(f.notifiedUnreachable.isEmpty())
            assertEquals(listOf(10L), f.verified)
        }

    @Test
    fun `an unreachable server is notified once, at the third run in a row`() = runTest {
        val f = Fakes()
        f.compatibility[1] = null

        repeat(2) { f.check.run() }
        assertTrue(f.notifiedUnreachable.isEmpty())

        f.check.run()
        f.check.run()

        assertEquals(listOf(1L), f.notifiedUnreachable)
        assertEquals(4, f.failures[1])
        assertTrue(f.verified.isEmpty())
    }

    @Test
    fun `servers are counted apart`() = runTest {
        val f = Fakes()
        f.groups =
            listOf(
                HealthCheck.Group(connectionId = 1, anonymousGroupInternalId = 10),
                HealthCheck.Group(connectionId = 2, anonymousGroupInternalId = 20),
            )
        f.compatibility[2] = null

        f.check.run()

        assertEquals(0, f.failures[1])
        assertEquals(1, f.failures[2])
        assertEquals(listOf(10L), f.verified)
    }

    @Test
    fun `an incompatible server answered, is notified once and its groups aren't touched`() =
        runTest {
            val f = Fakes()
            f.compatibility[1] = ApiCompatibility.ServerTooOld
            f.failures[1] = 2

            f.check.run()
            f.check.run()

            assertEquals(0, f.failures[1])
            assertEquals(listOf(1L to ApiCompatibility.ServerTooOld), f.notifiedIncompatible)
            assertTrue(f.verified.isEmpty())
        }

    @Test
    fun `a server that became compatible clears its notification`() = runTest {
        val f = Fakes()
        f.compatibility[1] = ApiCompatibility.AppTooOld
        f.check.run()

        f.compatibility[1] = ApiCompatibility.Compatible
        f.check.run()

        assertEquals(listOf(1L), f.clearedIncompatible)
        assertEquals(false, f.incompatibleNotified[1])
        assertEquals(listOf(10L), f.verified)
    }

    @Test
    fun `every run registers the pushes again`() = runTest {
        val f = Fakes()

        f.check.run()
        f.check.run()

        assertEquals(2, f.registrations)
    }

    @Test
    fun `without groups nothing is checked`() = runTest {
        val f = Fakes()
        f.groups = emptyList()

        f.check.run()

        assertEquals(0, f.registrations)
        assertTrue(f.clearedUnreachable.isEmpty())
    }
}
