package dev.saikrishna.todo.capture

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.ComposerDraft
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.ShareCapture
import dev.saikrishna.todo.ui.capture.CaptureScreen
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/** v0.13: the capture card carries the same add-task toolbar as the composer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class CaptureToolbarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val now = LocalDateTime.of(2026, 10, 23, 9, 0) // a Friday
    private var drafted: Triple<ComposerDraft.Draft, String, Long>? = null
    private var saved: Triple<QuickParse.Result, String, Long>? = null

    private fun show(withDraft: Boolean) {
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = true) {
                AppTheme(darkTheme = false) {
                    ProvideHaptics(userEnabled = false) {
                        CaptureScreen(
                            draft = ShareCapture.Draft("", "", emptyList()), lists = TaskList.Defaults, defaultList = TaskList.PERSONAL,
                            windowBlur = false, onSave = { p, n, l -> saved = Triple(p, n, l) }, onFinished = {},
                            clock = { now }, autoFocus = false,
                            onSaveDraft = if (withDraft) { d, n, l -> drafted = Triple(d, n, l) } else null,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tagged(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onNodeWithTag(tag, useUnmergedTree = true).performClick(); rule.waitForIdle() }
    private fun title(text: String) = rule.onNode(hasSetTextAction() and hasContentDescription("Task title")).performTextInput(text)

    @Test fun toolsAreAlwaysThereAndPicksReachTheDraft() {
        show(withDraft = true)
        assertTrue(tagged("composer-toolbar"))
        title("Gym")
        click("tool-Date"); click("preset-Tomorrow")
        click("tool-Time"); click("preset-6 pm")
        click("tool-List"); click("pick-list-${TaskList.WORK}")
        click("tool-Repeat"); click("preset-repeat-0")
        assertTrue(tagged("chip-Date")); assertTrue(tagged("chip-Time")); assertTrue(tagged("chip-Repeat"))
        click("capture-save")
        val (d, _, list) = drafted!!
        assertEquals("Gym", d.title)
        assertEquals(LocalDate.of(2026, 10, 24), d.dueDay)
        assertEquals(18 * 60, d.dueMinute)
        assertNotNull(d.repeat)
        assertEquals(TaskList.WORK, list)
    }

    @Test fun clearingAChipAndTheFallbackSave() {
        show(withDraft = false)
        title("Library books")
        click("tool-Date"); click("preset-Tomorrow")
        click("tool-Priority"); click("preset-High")
        click("chip-clear-Priority")
        assertFalse(tagged("chip-Priority"))
        click("capture-save")
        val (p, _, _) = saved!!
        assertEquals("Library books", p.title)
        assertEquals(LocalDate.of(2026, 10, 24), p.dueDay)
        assertTrue("priority cleared: ${p.priority}", p.priority == null || p.priority?.name == "None")
    }
}
