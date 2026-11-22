package com.peppeosmio.lockate.ui.screens.settings

import com.peppeosmio.lockate.utils.LockatePermission
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSummaryTest {
    @Test
    fun `a default url reads as the service's name`() {
        assertEquals("OpenFreeMap Liberty · default", urlSummary(SettingsUrl.LightStyle, null))
        assertEquals("Photon by Komoot · default", urlSummary(SettingsUrl.PlaceSearch, null))
    }

    @Test
    fun `a custom url reads without its scheme`() {
        assertEquals(
            "tiles.mymaps.example/styles/dark",
            urlSummary(SettingsUrl.DarkStyle, "https://tiles.mymaps.example/styles/dark/"),
        )
    }

    @Test
    fun `the permissions row says how location and notifications are set`() {
        assertEquals(
            "Location all the time · notifications on",
            permissionsSummary(
                mapOf(
                    LockatePermission.PreciseLocation to true,
                    LockatePermission.LocationAllTheTime to true,
                    LockatePermission.Notifications to true,
                )
            ),
        )
        assertEquals(
            "Location only while open · notifications off",
            permissionsSummary(
                mapOf(
                    LockatePermission.PreciseLocation to true,
                    LockatePermission.LocationAllTheTime to false,
                    LockatePermission.Notifications to false,
                )
            ),
        )
    }

    @Test
    fun `without the notifications permission to ask, the row says only location`() {
        assertEquals("No location", permissionsSummary(emptyMap()))
    }
}
