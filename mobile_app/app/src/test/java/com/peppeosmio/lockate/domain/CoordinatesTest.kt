package com.peppeosmio.lockate.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CoordinatesTest {

    @Test
    fun `toByteArray then fromByteArray round trip preserves latitude and longitude`() {
        val coordinates = Coordinates(latitude = 40.8517746, longitude = 14.2681244)

        val roundTripped = Coordinates.fromByteArray(coordinates.toByteArray())

        assertEquals(coordinates, roundTripped)
    }

    @Test
    fun `round trip preserves negative coordinates`() {
        val coordinates = Coordinates(latitude = -33.8688197, longitude = -70.6692655)

        val roundTripped = Coordinates.fromByteArray(coordinates.toByteArray())

        assertEquals(coordinates, roundTripped)
    }

    @Test
    fun `round trip preserves zero coordinates`() {
        val coordinates = Coordinates(latitude = 0.0, longitude = 0.0)

        val roundTripped = Coordinates.fromByteArray(coordinates.toByteArray())

        assertEquals(coordinates, roundTripped)
    }

    @Test
    fun `toByteArray produces exactly 16 bytes`() {
        assertEquals(16, Coordinates.NAPOLI.toByteArray().size)
    }

    @Test
    fun `latitude occupies the first 8 bytes and longitude the last 8 bytes`() {
        val coordinates = Coordinates(latitude = 1.0, longitude = 2.0)
        val bytes = coordinates.toByteArray()

        val latOnly = Coordinates(latitude = 1.0, longitude = 0.0).toByteArray()
        val lonOnly = Coordinates(latitude = 0.0, longitude = 2.0).toByteArray()

        assertEquals(latOnly.slice(0..7), bytes.slice(0..7))
        assertEquals(lonOnly.slice(8..15), bytes.slice(8..15))
    }

    @Test
    fun `toShareableString formats as lat,lng rounded to 6 decimals`() {
        assertEquals("40.851775,14.268124", Coordinates.NAPOLI.toShareableString())
    }

    @Test
    fun `toShareableString keeps the sign of negative coordinates`() {
        val coordinates = Coordinates(latitude = -33.8688197, longitude = -70.6692655)

        assertEquals("-33.868820,-70.669266", coordinates.toShareableString())
    }

    @Test
    fun `toShareableString uses a dot decimal separator regardless of default locale`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ITALY)
        try {
            assertEquals("40.851775,14.268124", Coordinates.NAPOLI.toShareableString())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
