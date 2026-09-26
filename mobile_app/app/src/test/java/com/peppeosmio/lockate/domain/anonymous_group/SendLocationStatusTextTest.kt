package com.peppeosmio.lockate.domain.anonymous_group

import org.junit.Assert.assertEquals
import org.junit.Test

class SendLocationStatusTextTest {

    private fun status(
        totalAGCount: Int = 1,
        activeAGCount: Int = 0,
        isLocationDisabled: Boolean = false,
        isLocationUnavailable: Boolean = false,
    ) = SendLocationStatus(
        totalAGCount = totalAGCount,
        activeAGCount = activeAGCount,
        isLocationDisabled = isLocationDisabled,
        isLocationUnavailable = isLocationUnavailable,
    )

    @Test
    fun `no groups takes precedence over everything`() {
        assertEquals(
            SendLocationStatus.TEXT_NO_GROUPS,
            status(totalAGCount = 0, isLocationDisabled = true, isLocationUnavailable = true)
                .notificationText()
        )
    }

    @Test
    fun `location disabled beats location unavailable`() {
        assertEquals(
            SendLocationStatus.TEXT_LOCATION_DISABLED,
            status(isLocationDisabled = true, isLocationUnavailable = true).notificationText()
        )
    }

    @Test
    fun `location unavailable shown when not disabled`() {
        assertEquals(
            SendLocationStatus.TEXT_LOCATION_UNAVAILABLE,
            status(isLocationUnavailable = true).notificationText()
        )
    }

    @Test
    fun `no active connections shows server unreachable`() {
        assertEquals(SendLocationStatus.TEXT_SERVER_UNREACHABLE, status(activeAGCount = 0).notificationText())
    }

    @Test
    fun `one active connection`() {
        assertEquals(SendLocationStatus.TEXT_SHARING_ONE, status(activeAGCount = 1).notificationText())
    }

    @Test
    fun `multiple active connections`() {
        assertEquals(
            "${SendLocationStatus.TEXT_SHARING_MANY_PREFIX}3${SendLocationStatus.TEXT_SHARING_MANY_SUFFIX}",
            status(totalAGCount = 3, activeAGCount = 3).notificationText()
        )
    }
}
