package com.peppeosmio.lockate.service

import com.peppeosmio.lockate.domain.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchServiceTest {

    private fun feature(lon: Double, lat: Double, properties: String) =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":$properties}"""

    private fun collection(vararg features: String) =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    @Test
    fun `a named place is described by its street, city and country`() {
        val json = collection(
            feature(
                12.4922, 41.8902,
                """{"osm_id":1,"name":"Colosseo","street":"Piazza del Colosseo","housenumber":"1","city":"Roma","state":"Lazio","country":"Italia"}"""
            )
        )

        val places = parsePhotonPlaces(json)

        assertEquals(
            listOf(
                Place(
                    name = "Colosseo",
                    description = "Piazza del Colosseo 1, Roma, Lazio, Italia",
                    coordinates = Coordinates(latitude = 41.8902, longitude = 12.4922)
                )
            ),
            places
        )
    }

    @Test
    fun `an address without a name is named after its street and number`() {
        val json = collection(
            feature(14.25, 40.84, """{"street":"Via Toledo","housenumber":"10","city":"Napoli","country":"Italia"}""")
        )

        val place = parsePhotonPlaces(json).single()

        assertEquals("Via Toledo 10", place.name)
        assertEquals("Napoli, Italia", place.description)
    }

    @Test
    fun `a city is not repeated in its own description`() {
        val json = collection(
            feature(14.25, 40.85, """{"name":"Napoli","city":"Napoli","state":"Campania","country":"Italia"}""")
        )

        assertEquals("Campania, Italia", parsePhotonPlaces(json).single().description)
    }

    @Test
    fun `features with neither a name nor a street are skipped`() {
        val json = collection(
            feature(0.0, 0.0, """{"country":"Nowhere"}"""),
            feature(1.0, 2.0, """{"name":"Somewhere"}""")
        )

        val places = parsePhotonPlaces(json)

        assertEquals(listOf("Somewhere"), places.map { it.name })
        assertEquals("", places.single().description)
    }

    @Test
    fun `no features gives no places`() {
        assertEquals(emptyList<Place>(), parsePhotonPlaces(collection()))
    }
}
