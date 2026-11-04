package com.peppeosmio.lockate.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.peppeosmio.lockate.domain.Coordinates
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val DEFAULT_GEOCODER_URL = "https://photon.komoot.io"

data class Place(val name: String, val description: String, val coordinates: Coordinates)

@Serializable
private data class PhotonResponseDto(val features: List<PhotonFeatureDto>)

@Serializable
private data class PhotonFeatureDto(val geometry: PhotonGeometryDto, val properties: PhotonPropertiesDto)

@Serializable
private data class PhotonGeometryDto(val coordinates: List<Double>)

@Serializable
private data class PhotonPropertiesDto(
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
)

private val photonJson = Json { ignoreUnknownKeys = true }

/** The places of a Photon GeoJSON answer; features with neither a name nor a street are skipped. */
internal fun parsePhotonPlaces(json: String): List<Place> =
    photonJson.decodeFromString<PhotonResponseDto>(json).features.mapNotNull { feature ->
        val p = feature.properties
        val street = p.street?.let { listOfNotNull(it, p.housenumber).joinToString(" ") }
        val name = p.name ?: street ?: return@mapNotNull null
        val description = listOfNotNull(street.takeIf { p.name != null }, p.city, p.state, p.country)
            .filter { it != name }
            .distinct()
            .joinToString(", ")
        // GeoJSON puts the longitude first
        val (longitude, latitude) = feature.geometry.coordinates
        Place(name, description, Coordinates(latitude = latitude, longitude = longitude))
    }

/** Searches places with a Photon geocoder, which sees the query and the area it is biased to. */
class PlaceSearchService(private val context: Context, private val httpClient: HttpClient) {

    /** Null while the default geocoder is used */
    val geocoderUrl: Flow<String?> = context.dataStore.data.map { it[ConfigSettings.GEOCODER_URL] }

    /** A blank value is stored as absent, so the default geocoder is used */
    suspend fun save(url: String?) {
        context.dataStore.edit { prefs ->
            val geocoderUrl = url?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            if (geocoderUrl != null) prefs[ConfigSettings.GEOCODER_URL] = geocoderUrl
            else prefs.remove(ConfigSettings.GEOCODER_URL)
        }
    }

    /** Places matching [query], the ones closest to [near] first. */
    suspend fun search(query: String, near: Coordinates): List<Place> {
        val response = httpClient.get("${geocoderUrl.first() ?: DEFAULT_GEOCODER_URL}/api") {
            parameter("q", query)
            parameter("limit", MAX_RESULTS)
            parameter("lat", near.latitude)
            parameter("lon", near.longitude)
        }
        check(response.status.isSuccess()) { "The geocoder answered ${response.status}" }
        return parsePhotonPlaces(response.bodyAsText())
    }

    suspend fun isValidGeocoderUrl(url: String): Boolean = try {
        val response = httpClient.get("${url.trim().trimEnd('/')}/api") {
            parameter("q", "Roma")
            parameter("limit", 1)
        }
        response.status.isSuccess() && parsePhotonPlaces(response.bodyAsText()).isNotEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val MAX_RESULTS = 5
    }
}
