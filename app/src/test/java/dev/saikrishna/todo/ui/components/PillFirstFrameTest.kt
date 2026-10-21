package dev.saikrishna.todo.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.LightAppColors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Regression for the v0.4 pill flicker: the selected capsule must be drawn in the FIRST frame a
 * freshly composed pill is drawn (e.g. Home recreated by a Nav3 pop), with the clock paused so no
 * animation or effect gets a chance to run first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi", application = Application::class)
class PillFirstFrameTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun capsuleIsUnderSelectedSegmentInFirstFrame() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ProvideMotion(userPrefReduceMotion = false) {
                AppTheme(darkTheme = false) { ProvideHaptics(userEnabled = false) {
                    SegmentedPill(
                        options = listOf("Today", "Upcoming", "Done"),
                        selected = "Upcoming",
                        label = { it },
                        onSelect = {},
                        style = PillStyle.Floating,
                        modifier = Modifier.testTag("pill"),
                    )
                } }
            }
        }
        val px = rule.onNodeWithTag("pill").captureToImage().toPixelMap()
        val y = px.height / 2
        // Sample just inside the middle segment's left edge (clear of the label) and the same spot in segment 0.
        val third = px.width / 3
        val inMiddle = px[third + third / 8, y]
        val inFirst = px[third / 8 + 2, y]
        assertTrue("capsule missing under the selected segment: $inMiddle", inMiddle.isClose(LightAppColors.ink))
        assertTrue("capsule drawn under an unselected segment: $inFirst", !inFirst.isClose(LightAppColors.ink))
    }

    private fun Color.isClose(o: Color) =
        abs(red - o.red) < 0.08f && abs(green - o.green) < 0.08f && abs(blue - o.blue) < 0.08f
}
