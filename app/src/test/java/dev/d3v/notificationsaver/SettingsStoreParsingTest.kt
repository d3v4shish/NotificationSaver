package dev.d3v.notificationsaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreParsingTest {
    @Test
    fun decodeAppCategoryOverrides_readsStoredOverrides() {
        val parsed = decodeAppCategoryOverrides("""{"com.whatsapp":"Messages","com.bank":"Finance"}""")

        assertEquals(
            mapOf(
                "com.whatsapp" to "Messages",
                "com.bank" to "Finance",
            ),
            parsed,
        )
    }

    @Test
    fun decodeAppCategoryOverrides_invalidJsonReturnsEmptyMap_andReportsError() {
        var failure: Throwable? = null

        val parsed = decodeAppCategoryOverrides("{broken") {
            failure = it
        }

        assertTrue(parsed.isEmpty())
        assertNotNull(failure)
    }

    @Test
    fun decodeThemeMode_unknownValueFallsBackToSystem() {
        assertEquals(ThemeMode.Dark, decodeThemeMode(ThemeMode.Dark.name))
        assertEquals(ThemeMode.System, decodeThemeMode("Nope"))
        assertEquals(ThemeMode.System, decodeThemeMode(null))
    }

    @Test
    fun decodePinnedThreadIds_trimsFiltersAndDeduplicatesValues() {
        val parsed = decodePinnedThreadIds("""[" family ","","family","work"]""")

        assertEquals(setOf("family", "work"), parsed)
    }
}
