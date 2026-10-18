package com.peppeosmio.lockate.ui.screens.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

const val DEFAULT_LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
const val DEFAULT_DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

fun resolveMapStyleUrl(isDarkTheme: Boolean, customLight: String?, customDark: String?): String {
    val custom = (if (isDarkTheme) customDark else customLight)?.trim()
    return custom?.takeIf { it.isNotEmpty() }
        ?: if (isDarkTheme) DEFAULT_DARK_STYLE_URL else DEFAULT_LIGHT_STYLE_URL
}

fun isValidStyleJson(json: String): Boolean {
    val style = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return false
    return "version" in style && "sources" in style
}
