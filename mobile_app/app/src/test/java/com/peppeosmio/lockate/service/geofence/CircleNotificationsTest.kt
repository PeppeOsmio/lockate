package com.peppeosmio.lockate.service.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CircleNotificationsTest {
    private var now = 1_000_000L
    private val book = CircleNotificationBook { now }

    private fun events(group: String, member: String, vararg texts: String) =
        CircleEvents(group, "Group $group", member, texts.toList())

    @Test
    fun `a new event of the same member replaces the previous one`() {
        val first = book.record(events("g1", "m1", "Mario arrived at Home"))
        now += 60_000
        val second = book.record(events("g1", "m1", "Mario left Home"))

        assertEquals(first.member.tag, second.member.tag)
        assertEquals("Mario left Home", second.member.text)
        assertEquals(listOf("Mario left Home"), second.summary.lines)
    }

    @Test
    fun `members have their own notification`() {
        val mario = book.record(events("g1", "m1", "Mario arrived at Home"))
        val luca = book.record(events("g1", "m2", "Luca left School"))

        assertNotEquals(mario.member.tag, luca.member.tag)
        assertEquals(mario.summary.tag, luca.summary.tag)
    }

    @Test
    fun `the summary lists the last event of every member, most recent first`() {
        book.record(events("g1", "m1", "Mario arrived at Home"))
        now += 60_000
        val update = book.record(events("g1", "m2", "Luca left School"))

        assertEquals(listOf("Luca left School", "Mario arrived at Home"), update.summary.lines)
        assertEquals("Luca left School", update.summary.text)
    }

    @Test
    fun `groups have their own tags, keys and summaries`() {
        val a = book.record(events("g1", "m1", "Mario arrived at Home"))
        val b = book.record(events("g2", "m1", "Mario arrived at School"))

        assertNotEquals(a.member.tag, b.member.tag)
        assertNotEquals(a.summary.tag, b.summary.tag)
        assertNotEquals(a.member.groupKey, b.member.groupKey)
        assertEquals(a.member.groupKey, a.summary.groupKey)
        assertEquals(listOf("Mario arrived at School"), b.summary.lines)
        assertEquals("Group g2", b.summary.title)
    }

    @Test
    fun `an event is dropped from the summary after the timeout`() {
        book.record(events("g1", "m1", "Mario arrived at Home"))

        now += CIRCLE_NOTIFICATION_TIMEOUT_MILLIS - 1
        val before = book.record(events("g1", "m2", "Luca left School"))
        assertEquals(listOf("Luca left School", "Mario arrived at Home"), before.summary.lines)

        now += 1
        val after = book.record(events("g1", "m3", "Anna left Work"))
        assertEquals(listOf("Anna left Work", "Luca left School"), after.summary.lines)
    }

    @Test
    fun `a message with two transitions shows the last as text and both as lines`() {
        val update = book.record(events("g1", "m1", "Mario left School", "Mario arrived at Home"))

        assertEquals("Mario arrived at Home", update.member.text)
        assertEquals(listOf("Mario left School", "Mario arrived at Home"), update.member.lines)
        assertEquals(listOf("Mario arrived at Home"), update.summary.lines)
    }

    @Test
    fun `notifications are stamped with the time of the event`() {
        now = 5_000
        val update = book.record(events("g1", "m1", "Mario arrived at Home"))

        assertEquals(5_000L, update.member.whenMillis)
        assertEquals(5_000L, update.summary.whenMillis)
    }
}
