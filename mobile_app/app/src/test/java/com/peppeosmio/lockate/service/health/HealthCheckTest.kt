package com.peppeosmio.lockate.service.health

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckTest {

    private class Fakes {
        var groups = listOf(HealthCheck.Group(connectionId = 1, anonymousGroupInternalId = 10))
        val outcomes = mutableMapOf<Long, HealthCheck.Outcome>()
        val failures = mutableMapOf<Long, Int>()
        val notified = mutableListOf<Long>()
        val cleared = mutableListOf<Long>()
        var registrations = 0

        val check =
            HealthCheck(
                listGroups = { groups },
                verify = { outcomes[it.anonymousGroupInternalId] ?: HealthCheck.Outcome.Ok },
                registerPushes = { registrations++ },
                failures =
                    object : HealthCheck.FailureCounts {
                        override suspend fun get(connectionId: Long) = failures[connectionId] ?: 0

                        override suspend fun set(connectionId: Long, count: Int) {
                            failures[connectionId] = count
                        }
                    },
                notifyUnreachable = { notified += it },
                clearUnreachable = { cleared += it },
            )
    }

    @Test
    fun `a reachable server resets its count and clears its notification`() = runTest {
        val f = Fakes()
        f.failures[1] = 2

        f.check.run()

        assertEquals(0, f.failures[1])
        assertEquals(listOf(1L), f.cleared)
        assertTrue(f.notified.isEmpty())
    }

    @Test
    fun `an unreachable server is notified once, at the third run in a row`() = runTest {
        val f = Fakes()
        f.outcomes[10] = HealthCheck.Outcome.Unreachable

        repeat(2) { f.check.run() }
        assertTrue(f.notified.isEmpty())

        f.check.run()
        f.check.run()

        assertEquals(listOf(1L), f.notified)
        assertEquals(4, f.failures[1])
    }

    @Test
    fun `one group answering makes its server reachable`() = runTest {
        val f = Fakes()
        f.groups =
            listOf(
                HealthCheck.Group(connectionId = 1, anonymousGroupInternalId = 10),
                HealthCheck.Group(connectionId = 1, anonymousGroupInternalId = 11),
            )
        f.outcomes[10] = HealthCheck.Outcome.Unreachable
        f.failures[1] = 5

        f.check.run()

        assertEquals(0, f.failures[1])
    }

    @Test
    fun `a removed member or a deleted group is an answer`() = runTest {
        val f = Fakes()
        f.outcomes[10] = HealthCheck.Outcome.Gone
        f.failures[1] = 2

        f.check.run()

        assertEquals(0, f.failures[1])
    }

    @Test
    fun `servers are counted apart`() = runTest {
        val f = Fakes()
        f.groups =
            listOf(
                HealthCheck.Group(connectionId = 1, anonymousGroupInternalId = 10),
                HealthCheck.Group(connectionId = 2, anonymousGroupInternalId = 20),
            )
        f.outcomes[20] = HealthCheck.Outcome.Unreachable

        f.check.run()

        assertEquals(0, f.failures[1])
        assertEquals(1, f.failures[2])
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
        assertTrue(f.cleared.isEmpty())
    }
}
