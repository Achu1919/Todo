package dev.saikrishna.todo.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import dev.saikrishna.todo.domain.FocusMode
import dev.saikrishna.todo.domain.FocusSchedule
import java.time.DayOfWeek
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

enum class ThemeMode {
    System, Light, Dark;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun parse(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: System
    }
}

@Immutable
data class AppSettings(
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
) {
    companion object {
        /** Pure mapping from stored values (any may be missing) to settings with defaults. */
        fun from(reduceMotion: Boolean?, haptics: Boolean?, theme: String?): AppSettings = AppSettings(
            reduceMotion = reduceMotion ?: false,
            haptics = haptics ?: true,
            theme = ThemeMode.parse(theme),
        )
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val ReduceMotion = booleanPreferencesKey("reduce_motion")
        val Haptics = booleanPreferencesKey("haptics")
        val Theme = stringPreferencesKey("theme")
        val AskedNotifications = booleanPreferencesKey("asked_notifications")
        val FocusManual = stringPreferencesKey("focus_manual")
        val FocusManualAt = longPreferencesKey("focus_manual_at")
        val FocusAuto = booleanPreferencesKey("focus_auto")
        val FocusDays = stringPreferencesKey("focus_days")
        val FocusStart = intPreferencesKey("focus_start")
        val FocusEnd = intPreferencesKey("focus_end")
        val FocusOutside = stringPreferencesKey("focus_outside")
        val PlanEnabled = booleanPreferencesKey("plan_enabled")
        val PlanMinute = intPreferencesKey("plan_minute")
    }

    /** v0.8 morning planning: off by default; 8:00 when turned on. */
    val plan: Flow<PlanPrefs> = store.data.map { p -> PlanPrefs(p[Keys.PlanEnabled] ?: false, p[Keys.PlanMinute] ?: PlanPrefs.DefaultMinute) }
    suspend fun setPlan(prefs: PlanPrefs) {
        store.edit { it[Keys.PlanEnabled] = prefs.enabled; it[Keys.PlanMinute] = prefs.minute.coerceIn(0, 24 * 60 - 1) }
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings.from(p[Keys.ReduceMotion], p[Keys.Haptics], p[Keys.Theme])
    }

    suspend fun setReduceMotion(on: Boolean) { store.edit { it[Keys.ReduceMotion] = on } }
    suspend fun setHaptics(on: Boolean) { store.edit { it[Keys.Haptics] = on } }
    suspend fun setTheme(mode: ThemeMode) { store.edit { it[Keys.Theme] = mode.name } }

    /** v0.6: the notification permission is asked once, in context (first timed task). */
    val askedNotifications: Flow<Boolean> = store.data.map { it[Keys.AskedNotifications] ?: false }
    suspend fun markAskedNotifications() { store.edit { it[Keys.AskedNotifications] = true } }

    /** v0.7 focus: the manual pick (with when it was made) and the automatic schedule. */
    val focus: Flow<FocusPrefs> = store.data.map { p ->
        val d = FocusSchedule()
        FocusPrefs(
            manual = FocusMode.parse(p[Keys.FocusManual]),
            manualAt = p[Keys.FocusManualAt],
            schedule = FocusSchedule(
                enabled = p[Keys.FocusAuto] ?: false,
                days = p[Keys.FocusDays]?.let(::parseDays) ?: d.days,
                start = p[Keys.FocusStart] ?: d.start,
                end = p[Keys.FocusEnd] ?: d.end,
                outside = FocusMode.parse(p[Keys.FocusOutside]) ?: d.outside,
            ),
        )
    }

    suspend fun setFocus(mode: FocusMode, at: Long) {
        store.edit { it[Keys.FocusManual] = mode.name; it[Keys.FocusManualAt] = at }
    }

    suspend fun setSchedule(s: FocusSchedule) {
        store.edit {
            it[Keys.FocusAuto] = s.enabled
            it[Keys.FocusDays] = s.days.sorted().joinToString(",") { d -> d.value.toString() }
            it[Keys.FocusStart] = s.start
            it[Keys.FocusEnd] = s.end
            it[Keys.FocusOutside] = s.outside.name
            // Turning the schedule on hands control to it.
            it.remove(Keys.FocusManual); it.remove(Keys.FocusManualAt)
        }
    }

    /**
     * v0.10 backup: every user preference as JSON (per-device state such as "asked for the
     * notification permission" is left out). Restored by [importPrefs], which only takes keys it
     * knows with the right type, so an odd or future file cannot write garbage.
     */
    suspend fun exportPrefs(): org.json.JSONObject {
        val p = store.data.first()
        val o = org.json.JSONObject()
        for (k in BackupBooleans) p[k]?.let { o.put(k.name, it) }
        for (k in BackupStrings) p[k]?.let { o.put(k.name, it) }
        for (k in BackupInts) p[k]?.let { o.put(k.name, it) }
        for (k in BackupLongs) p[k]?.let { o.put(k.name, it) }
        return o
    }

    suspend fun importPrefs(o: org.json.JSONObject) {
        store.edit { e ->
            for (k in BackupBooleans) if (o.has(k.name)) (o.opt(k.name) as? Boolean)?.let { e[k] = it }
            for (k in BackupStrings) if (o.has(k.name)) (o.opt(k.name) as? String)?.let { e[k] = it.take(200) }
            for (k in BackupInts) if (o.has(k.name)) (o.opt(k.name) as? Number)?.let { e[k] = it.toInt() }
            for (k in BackupLongs) if (o.has(k.name)) (o.opt(k.name) as? Number)?.let { e[k] = it.toLong() }
        }
    }

    private val BackupBooleans = listOf(Keys.ReduceMotion, Keys.Haptics, Keys.FocusAuto, Keys.PlanEnabled)
    private val BackupStrings = listOf(Keys.Theme, Keys.FocusManual, Keys.FocusDays, Keys.FocusOutside)
    private val BackupInts = listOf(Keys.FocusStart, Keys.FocusEnd, Keys.PlanMinute)
    private val BackupLongs = listOf(Keys.FocusManualAt)

    private fun parseDays(s: String): Set<DayOfWeek> =
        s.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { v -> v in 1..7 }?.let(DayOfWeek::of) }.toSet()
}

@Immutable
data class FocusPrefs(
    val manual: FocusMode? = null,
    val manualAt: Long? = null,
    val schedule: FocusSchedule = FocusSchedule(),
)

@Immutable
data class PlanPrefs(val enabled: Boolean = false, val minute: Int = DefaultMinute) {
    companion object { const val DefaultMinute = 8 * 60 }
}
