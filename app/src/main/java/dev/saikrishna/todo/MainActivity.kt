package dev.saikrishna.todo

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import dev.saikrishna.todo.reminders.Reminders
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.nav.AppNav
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.ThemeReveal
import dev.saikrishna.todo.ui.theme.ThemeRevealHost
import dev.saikrishna.todo.ui.theme.InterFamily
import dev.saikrishna.todo.ui.theme.JetBrainsMonoFamily
import dev.saikrishna.todo.ui.theme.SerifFamily
import androidx.compose.ui.platform.LocalFontFamilyResolver

class MainActivity : ComponentActivity() {
    /** v0.9.1 in-place theme switch (kept across recompositions; not across process death). */
    internal val reveal = ThemeReveal()
    /** v0.10 cold-start entrance (plays once per process, on a fresh Activity only). */
    internal lateinit var entrance: dev.saikrishna.todo.ui.motion.LaunchEntrance

    private val vm: TodoViewModel by viewModels {
        val app = application as TodoApp
        TodoViewModel.factory(app.repository, app.settings)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until settings and the first tasks are read, so the first frame has
        // the right theme and the launch entrance has its rows (both reads take a few ms).
        splash.setKeepOnScreenCondition { vm.settings.value == null || vm.tasks.value == null }
        // v0.10: the splash sits on the backdrop colour; it hands over with a short fade while the
        // icon eases up a touch, and the entrance plays underneath.
        splash.setOnExitAnimationListener { provider ->
            provider.iconView.animate().scaleX(1.06f).scaleY(1.06f).setDuration(180).start()
            provider.view.animate().alpha(0f).setDuration(180).withEndAction { provider.remove() }.start()
        }
        val app = application as TodoApp
        entrance = dev.saikrishna.todo.ui.motion.LaunchEntrance(play = savedInstanceState == null && !app.entranceShown)
        app.entranceShown = true
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        if (savedInstanceState == null) handleOpen(intent)

        setContent {
            // Warm the font cache while the splash is still up, so the first text layout of each
            // family (e.g. the serif heading on a page switch) never loads a font mid-animation.
            val fonts = LocalFontFamilyResolver.current
            LaunchedEffect(Unit) {
                for (family in listOf(InterFamily, SerifFamily, JetBrainsMonoFamily)) runCatching { fonts.preload(family) }
            }
            val stored by vm.settings.collectAsStateWithLifecycle()
            val settings = stored ?: return@setContent
            val storedDark = settings.theme.isDark(isSystemInDarkTheme())
            // v0.9.1: the header toggle switches the theme in place (circular reveal), never by
            // recreating the Activity; the override holds until the stored setting matches.
            val override = reveal.overrideDark
            val holding = reveal.holding
            LaunchedEffect(storedDark, override, holding) { if (!holding && override != null && override == storedDark) reveal.overrideDark = null }
            val dark = override ?: storedDark
            val barsDark = reveal.barsDark ?: dark
            LaunchedEffect(barsDark) { applySystemBars(barsDark) }
            ProvideMotion(userPrefReduceMotion = settings.reduceMotion) {
                ThemeRevealHost(reveal) {
                    AppTheme(darkTheme = dark) {
                        ProvideHaptics(userEnabled = settings.haptics) {
                            androidx.compose.runtime.CompositionLocalProvider(
                                dev.saikrishna.todo.ui.motion.LocalLaunchEntrance provides entrance,
                            ) { AppNav(vm) }
                        }
                    }
                }
            }
        }
    }

    /** Status/navigation bar icons: light icons on the dark theme and vice versa. */
    internal fun applySystemBars(dark: Boolean) {
        val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        window.isNavigationBarContrastEnforced = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    /** A tapped reminder opens its task's panel. */
    private fun handleOpen(intent: Intent?) {
        val id = intent?.getLongExtra(Reminders.EXTRA_OPEN_TASK, -1L) ?: -1L
        if (id >= 0) vm.requestOpen(id)
        if (intent?.getBooleanExtra(dev.saikrishna.todo.reminders.PlanReminder.EXTRA_OPEN_PLAN, false) == true) vm.requestPlan()
    }
}
