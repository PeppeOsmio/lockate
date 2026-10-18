package com.peppeosmio.lockate.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStylesTest {

    @Test
    fun `defaults are used when there are no custom styles`() {
        assertEquals(DEFAULT_LIGHT_STYLE_URL, resolveMapStyleUrl(isDarkTheme = false, customLight = null, customDark = null))
        assertEquals(DEFAULT_DARK_STYLE_URL, resolveMapStyleUrl(isDarkTheme = true, customLight = null, customDark = null))
    }

    @Test
    fun `custom style is used for the matching theme only`() {
        assertEquals("https://example.com/light.json", resolveMapStyleUrl(false, "https://example.com/light.json", null))
        assertEquals(DEFAULT_DARK_STYLE_URL, resolveMapStyleUrl(true, "https://example.com/light.json", null))
        assertEquals("https://example.com/dark.json", resolveMapStyleUrl(true, null, "https://example.com/dark.json"))
        assertEquals(DEFAULT_LIGHT_STYLE_URL, resolveMapStyleUrl(false, null, "https://example.com/dark.json"))
    }

    @Test
    fun `blank custom styles fall back to the defaults`() {
        assertEquals(DEFAULT_LIGHT_STYLE_URL, resolveMapStyleUrl(false, "", null))
        assertEquals(DEFAULT_DARK_STYLE_URL, resolveMapStyleUrl(true, null, "   "))
    }

    @Test
    fun `custom style is trimmed`() {
        assertEquals("https://example.com/light.json", resolveMapStyleUrl(false, "  https://example.com/light.json \n", null))
    }

    @Test
    fun `a style with version and sources is valid`() {
        assertTrue(isValidStyleJson("""{"version": 8, "sources": {}, "layers": []}"""))
    }

    @Test
    fun `a style without sources or version is not valid`() {
        assertFalse(isValidStyleJson("""{"version": 8, "layers": []}"""))
        assertFalse(isValidStyleJson("""{"sources": {}}"""))
    }

    @Test
    fun `text that is not a json object is not valid`() {
        assertFalse(isValidStyleJson("<html>not a style</html>"))
        assertFalse(isValidStyleJson("[]"))
        assertFalse(isValidStyleJson(""))
    }
}
