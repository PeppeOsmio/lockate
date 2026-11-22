package com.peppeosmio.lockate.ui.screens.permissions

import com.peppeosmio.lockate.utils.LockatePermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionsStateTest {
    @Test
    fun `the next permission is the first one still off, in screen order`() {
        val state =
            PermissionsState(
                granted =
                    mapOf(
                        LockatePermission.PreciseLocation to true,
                        LockatePermission.LocationAllTheTime to false,
                        LockatePermission.Notifications to false,
                        LockatePermission.PhysicalActivity to false,
                    )
            )
        assertEquals(LockatePermission.LocationAllTheTime, state.next)
    }

    @Test
    fun `nothing is highlighted when everything is on`() {
        assertNull(
            PermissionsState(granted = LockatePermission.entries.associateWith { true }).next
        )
    }
}
