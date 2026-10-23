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

class SettingsV12Test {
    @Test fun openOnDefaultsToTodo() {
        assertEquals(StartPage.Todo, AppSettings.from(null, null, null).openOn)
        assertEquals(StartPage.Todo, AppSettings().openOn)
        assertEquals(StartPage.Calendar, AppSettings.from(null, null, null, openOn = "Calendar").openOn)
        assertEquals(StartPage.Todo, StartPage.parse("Upcoming?"))
        assertEquals(StartPage.Todo, StartPage.parse(null))
    }

    /** v0.13: v0.12 values map onto the new pages and are rewritten once; current values stay. */
    @Test fun openOnMigratesV012Values() {
        assertEquals(StartPage.Home, AppSettings.from(null, null, null, openOn = "Overview").openOn)
        assertEquals(StartPage.Todo, AppSettings.from(null, null, null, openOn = "Today").openOn)
        assertEquals("Home", StartPage.migrated("Overview"))
        assertEquals("Todo", StartPage.migrated("Today"))
        assertEquals("Todo", StartPage.migrated("Bogus"))
        assertEquals(null, StartPage.migrated("Home"))
        assertEquals(null, StartPage.migrated("Calendar"))
        assertEquals(null, StartPage.migrated(null))
    }

    @Test fun greetingNameIsTrimmedAndCapped() {
        assertEquals("", AppSettings.from(null, null, null).name)
        assertEquals("Sai", AppSettings.from(null, null, null, name = "  Sai ").name)
        assertEquals(AppSettings.NameMax, AppSettings.from(null, null, null, name = "x".repeat(60)).name.length)
    }
}
