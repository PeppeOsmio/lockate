package com.peppeosmio.lockate.domain.anonymous_group

import org.junit.Assert.assertEquals
import org.junit.Test

class SendLocationStatusTextTest {

    @Test
    fun `sharing says so, whatever the posts do`() {
        assertEquals(
            SendLocationStatus.TEXT_SHARING,
            SendLocationStatus(sharedAGCount = 3, isLocationOff = false).notificationText(),
        )
    }

    @Test
    fun `location off is shown, as sharing can't work then`() {
        assertEquals(
            SendLocationStatus.TEXT_LOCATION_OFF,
            SendLocationStatus(sharedAGCount = 1, isLocationOff = true).notificationText(),
        )
    }
}
