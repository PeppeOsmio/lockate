package com.peppeosmio.lockate.platform_service

import com.google.android.gms.location.DetectedActivity
import com.peppeosmio.lockate.domain.UserActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class UserActivityOfTest {

    @Test
    fun `detected activities map to the user activity`() {
        assertEquals(UserActivity.Still, userActivityOf(DetectedActivity.STILL))
        assertEquals(UserActivity.OnFoot, userActivityOf(DetectedActivity.WALKING))
        assertEquals(UserActivity.OnFoot, userActivityOf(DetectedActivity.RUNNING))
        assertEquals(UserActivity.OnBicycle, userActivityOf(DetectedActivity.ON_BICYCLE))
        assertEquals(UserActivity.InVehicle, userActivityOf(DetectedActivity.IN_VEHICLE))
        assertEquals(UserActivity.Unknown, userActivityOf(DetectedActivity.TILTING))
    }
}
