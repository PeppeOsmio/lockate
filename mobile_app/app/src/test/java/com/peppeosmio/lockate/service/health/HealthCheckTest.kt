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
        val incompatibleNotified = mutableMapOf<Long, Boolean>()
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
                        override suspend fun incompatibleNotified(connectionId: Long) =
                            incompatibleNotified[connectionId] ?: false

                        override suspend fun setIncompatibleNotified(
                            connectionId: Long,
                            notified: Boolean,
                        ) {
                            incompatibleNotified[connectionId] = notified
                        }
                    },
                notifyIncompatible = { id, compatibility ->
                    notifiedIncompatible += id to compatibility
                },
                clearIncompatible = { clearedIncompatible += it },
            )
    }

    @Test
    fun `a reachable server has its groups verified`() = runTest {
        val f = Fakes()

        f.check.run()

        assertEquals(listOf(10L), f.verified)
    }

    @Test
    fun `an unreachable server is skipped without a notification`() = runTest {
        val f = Fakes()
        f.compatibility[1] = null

        repeat(5) { f.check.run() }

        assertTrue(f.verified.isEmpty())
        assertTrue(f.notifiedIncompatible.isEmpty())
        assertEquals(5, f.registrations)
    }

    @Test
    fun `an unreachable server doesn't stop the others`() = runTest {
        val f = Fakes()
        f.groups =
            listOf(
                HealthCheck.Group(connectionId = 1, anonymousGroupInternalId = 10),
                HealthCheck.Group(connectionId = 2, anonymousGroupInternalId = 20),
            )
        f.compatibility[2] = null

        f.check.run()

        assertEquals(listOf(10L), f.verified)
    }

    @Test
    fun `an incompatible server answered, is notified once and its groups aren't touched`() =
        runTest {
            val f = Fakes()
            f.compatibility[1] = ApiCompatibility.ServerTooOld

            f.check.run()
            f.check.run()

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
        assertTrue(f.verified.isEmpty())
    }
}
