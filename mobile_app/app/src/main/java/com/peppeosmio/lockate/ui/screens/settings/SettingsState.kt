package com.peppeosmio.lockate.ui.screens.settings

import com.peppeosmio.lockate.utils.LockatePermission

/** A URL the user can point at another service */
enum class SettingsUrl {
    LightStyle,
    DarkStyle,
    PlaceSearch,
}

/** The URL being edited in the dialog. [isError]: the last check failed */
data class UrlDraft(
    val url: SettingsUrl,
    val text: String,
    val isChecking: Boolean = false,
    val isError: Boolean = false,
)

/** Saved URLs: null means the default */
data class SettingsState(
    val urls: Map<SettingsUrl, String?> = emptyMap(),
    val draft: UrlDraft? = null,
    val permissions: Map<LockatePermission, Boolean> = emptyMap(),
)

/** A saved URL as the row shows it: the default's name, or the address without its scheme */
fun urlSummary(url: SettingsUrl, saved: String?): String =
    saved?.substringAfter("://")?.trimEnd('/')
        ?: when (url) {
            SettingsUrl.LightStyle -> "OpenFreeMap Liberty · default"
            SettingsUrl.DarkStyle -> "OpenFreeMap Dark · default"
            SettingsUrl.PlaceSearch -> "Photon by Komoot · default"
        }

/** The Permissions row: "Location all the time · notifications on" */
fun permissionsSummary(granted: Map<LockatePermission, Boolean>): String {
    val location =
        when {
            granted[LockatePermission.LocationAllTheTime] == true -> "Location all the time"
            granted[LockatePermission.PreciseLocation] == true -> "Location only while open"
            else -> "No location"
        }
    val notifications =
        granted[LockatePermission.Notifications]?.let {
            if (it) "notifications on" else "notifications off"
        }
    return listOfNotNull(location, notifications).joinToString(" · ")
}
