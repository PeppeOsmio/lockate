package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import com.peppeosmio.lockate.domain.UserActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLabelTest {

    @Test
    fun `every activity has its own label`() {
        assertEquals("Unknown", UserActivity.Unknown.label)
        assertEquals("Still", UserActivity.Still.label)
        assertEquals("On foot", UserActivity.OnFoot.label)
        assertEquals("By bike", UserActivity.OnBicycle.label)
        assertEquals("In a vehicle", UserActivity.InVehicle.label)
    }
}
