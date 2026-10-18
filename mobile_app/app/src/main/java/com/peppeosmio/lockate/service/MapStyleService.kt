package com.peppeosmio.lockate.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.peppeosmio.lockate.ui.screens.map.isValidStyleJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class MapStyleUrls(val light: String? = null, val dark: String? = null)

class MapStyleService(private val context: Context, private val httpClient: HttpClient) {

    val mapStyleUrls: Flow<MapStyleUrls> = context.dataStore.data.map {
        MapStyleUrls(
            light = it[ConfigSettings.MAP_STYLE_URL_LIGHT], dark = it[ConfigSettings.MAP_STYLE_URL_DARK]
        )
    }

    /** Blank values are stored as absent, so the default style is used */
    suspend fun save(light: String?, dark: String?) {
        context.dataStore.edit { prefs ->
            val lightUrl = light?.trim()?.takeIf { it.isNotEmpty() }
            val darkUrl = dark?.trim()?.takeIf { it.isNotEmpty() }
            if (lightUrl != null) prefs[ConfigSettings.MAP_STYLE_URL_LIGHT] = lightUrl
            else prefs.remove(ConfigSettings.MAP_STYLE_URL_LIGHT)
            if (darkUrl != null) prefs[ConfigSettings.MAP_STYLE_URL_DARK] = darkUrl
            else prefs.remove(ConfigSettings.MAP_STYLE_URL_DARK)
        }
    }

    suspend fun isValidStyleUrl(url: String): Boolean = try {
        val response = httpClient.get(url.trim())
        response.status.isSuccess() && isValidStyleJson(response.bodyAsText())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
