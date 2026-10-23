package dev.saikrishna.todo.capture

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.ShareCapture
import dev.saikrishna.todo.ui.capture.CapturePreset
import dev.saikrishna.todo.ui.capture.CaptureScreen
import dev.saikrishna.todo.ui.capture.VoiceControl
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/** v0.12: the capture card honours widget presets (list, due today, start listening). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class CapturePresetScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val now = LocalDateTime.of(2026, 10, 23, 9, 0)
    private var saved: Triple<QuickParse.Result, String, Long>? = null

    private class FakeVoice(override val available: Boolean = true) : VoiceControl {
        var starts = 0; var stops = 0
        private var on by mutableStateOf(false)
        var onText: ((String) -> Unit)? = null
        override val listening: Boolean get() = on
        override fun start(onText: (String) -> Unit, onDone: (String?) -> Unit) { starts++; on = true; this.onText = onText }
        override fun stop() { stops++; on = false }
        override fun cancel() { on = false }
    }

    private fun show(preset: CapturePreset, voice: FakeVoice = FakeVoice()) {
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = true) {
                AppTheme(darkTheme = false) {
                    ProvideHaptics(userEnabled = false) {
                        CaptureScreen(
                            draft = ShareCapture.Draft("", "", emptyList()), lists = TaskList.Defaults, defaultList = TaskList.PERSONAL,
                            windowBlur = false, onSave = { p, n, l -> saved = Triple(p, n, l) }, onFinished = {},
                            clock = { now }, autoFocus = false, preset = preset, voiceOverride = voice,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun listPresetFilesIntoThatList() {
        show(CapturePreset(listId = TaskList.WORK))
        rule.onNode(hasSetTextAction() and androidx.compose.ui.test.hasContentDescription("Task title")).performTextInput("Send the deck")
        rule.onNodeWithTag("capture-save").performClick(); rule.waitForIdle()
        assertEquals(TaskList.WORK, saved?.third)
        assertNull(saved?.first?.dueDay)
    }

    @Test fun missingPresetListFallsBackToTheDefault() {
        show(CapturePreset(listId = 99))
        rule.onNode(hasSetTextAction() and androidx.compose.ui.test.hasContentDescription("Task title")).performTextInput("Water plants")
        rule.onNodeWithTag("capture-save").performClick(); rule.waitForIdle()
        assertEquals(TaskList.PERSONAL, saved?.third)
    }

    @Test fun dueTodayPresetSelectsTodayAndCanBeTurnedOff() {
        show(CapturePreset(dueToday = true))
        rule.onNodeWithTag("capture-header").assertTextContains("New task · Today")
        rule.onNodeWithTag("capture-today").assertIsSelected()
        rule.onNode(hasSetTextAction() and androidx.compose.ui.test.hasContentDescription("Task title")).performTextInput("Call mum")
        rule.onNodeWithTag("capture-save").performClick(); rule.waitForIdle()
        assertEquals(LocalDate.of(2026, 10, 23), saved?.first?.dueDay)
    }

    @Test fun todayChipToggles() {
        show(CapturePreset())
        rule.onNodeWithTag("capture-today").assertIsNotSelected().performClick()
        rule.onNodeWithTag("capture-today").assertIsSelected()
        rule.onNodeWithTag("capture-header").assertTextContains("New task · Today")
    }

    @Test fun voicePresetStartsListeningWithPermission() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val v = FakeVoice()
        show(CapturePreset(voice = true), v)
        assertEquals(1, v.starts)
        rule.onNodeWithTag("capture-header").assertTextContains("Listening…")
        rule.runOnIdle { v.onText!!("buy oat milk") }
        rule.onNodeWithTag("capture-voice").performClick(); rule.waitForIdle() // tap = stop
        assertFalse(v.listening)
        rule.onNodeWithTag("capture-save").performClick(); rule.waitForIdle()
        assertEquals("buy oat milk", saved?.first?.title)
    }

    @Test fun voicePresetOnAPhoneWithoutRecogniserShowsAHint() {
        val v = FakeVoice(available = false)
        show(CapturePreset(voice = true), v)
        assertEquals(0, v.starts)
        rule.onNodeWithTag("capture-header").assertTextContains("Voice input isn't available on this phone")
    }
}
