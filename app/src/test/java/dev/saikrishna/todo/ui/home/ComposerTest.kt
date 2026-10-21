package dev.saikrishna.todo.ui.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/** Composer + parser end to end: chips appear while typing; tapping one keeps the words as text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class ComposerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0) // Wednesday
    private var submitted: QuickParse.Result? = null

    private fun setUp() {
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = false) {
                    ProvideHaptics(userEnabled = false) {
                        Composer(state = rememberTextFieldState(), onSubmit = { submitted = it }, clock = { now })
                    }
                }
            }
        }
    }

    @Test fun chipsAppearAndSubmitCarriesTheParse() {
        setUp()
        rule.onNode(hasSetTextAction()).performTextInput("Dentist tomorrow at 4pm !high")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Tomorrow, tap to keep as text").assertExists()
        rule.onNodeWithContentDescription("4 pm, tap to keep as text").assertExists()
        rule.onNodeWithContentDescription("High, tap to keep as text").assertExists()
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()
        val r = submitted!!
        assertEquals("Dentist", r.title)
        assertEquals(LocalDate.of(2026, 10, 22), r.dueDay)
        assertEquals(16 * 60, r.dueMinute)
        assertEquals(Priority.High, r.priority)
    }

    @Test fun tappingAChipKeepsTheWords() {
        setUp()
        rule.onNode(hasSetTextAction()).performTextInput("Call mom tomorrow")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Tomorrow, tap to keep as text").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Tomorrow, tap to keep as text").assertDoesNotExist()
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()
        val r = submitted!!
        assertEquals("Call mom tomorrow", r.title)
        assertNull(r.dueDay)
    }
}
