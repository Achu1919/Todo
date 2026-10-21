package dev.saikrishna.todo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test fun defaultsWhenNothingStored() {
        assertEquals(AppSettings(reduceMotion = false, haptics = true, theme = ThemeMode.System), AppSettings.from(null, null, null))
    }

    @Test fun storedValuesMap() {
        assertEquals(AppSettings(true, false, ThemeMode.Dark), AppSettings.from(true, false, "Dark"))
        assertEquals(ThemeMode.Light, AppSettings.from(null, null, "Light").theme)
    }

    @Test fun unknownThemeFallsBackToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.parse("Sepia"))
        assertEquals(ThemeMode.System, ThemeMode.parse(""))
    }

    @Test fun themeResolution() {
        assertTrue(ThemeMode.System.isDark(systemDark = true))
        assertFalse(ThemeMode.System.isDark(systemDark = false))
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
    }
}
