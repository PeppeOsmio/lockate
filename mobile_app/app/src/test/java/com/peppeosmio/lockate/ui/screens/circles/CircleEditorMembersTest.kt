package com.peppeosmio.lockate.ui.screens.circles

import com.peppeosmio.lockate.domain.Coordinates
import com.peppeosmio.lockate.domain.LocationRecord
import com.peppeosmio.lockate.domain.UserActivity
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CircleEditorMembersTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    private val school = Coordinates(41.0, 14.0)

    private fun member(id: String, seenMinutesAgo: Int?) =
        AGMember(
            internalId = 1,
            id = id,
            name = id,
            createdAt = LocalDateTime(2026, 1, 1, 0, 0, 0),
            lastLocationRecord =
                seenMinutesAgo?.let {
                    LocationRecord(
                        coordinates = school,
                        timestamp = (now - it.minutes).toLocalDateTime(TimeZone.UTC),
                        activity = UserActivity.Still,
                    )
                },
            isAGAdmin = false,
        )

    @Test
    fun `only the other members with a position are shown`() {
        val points =
            memberPoints(
                listOf(member("me", 1), member("anna", 1), member("luca", null)),
                myMemberId = "me",
                now = now,
            )
        assertEquals(listOf("anna"), points.map { it.id })
        assertEquals(school, points.single().coordinates)
    }

    @Test
    fun `a member no longer live is faded`() {
        val points = memberPoints(listOf(member("anna", 1), member("luca", 60)), "me", now)
        assertEquals(listOf(false, true), points.map { it.isOld })
    }
}
