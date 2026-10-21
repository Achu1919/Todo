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
import dev.saikrishna.todo.ui.theme.InterFamily
import dev.saikrishna.todo.ui.theme.JetBrainsMonoFamily
import dev.saikrishna.todo.ui.theme.SerifFamily
import androidx.compose.ui.platform.LocalFontFamilyResolver

class MainActivity : ComponentActivity() {
    private val vm: TodoViewModel by viewModels {
        val app = application as TodoApp
        TodoViewModel.factory(app.repository, app.settings)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until settings are read, so the first frame has the right theme.
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(150).withEndAction { provider.remove() }.start()
        }
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
            val dark = settings.theme.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                window.isNavigationBarContrastEnforced = false
            }
            ProvideMotion(userPrefReduceMotion = settings.reduceMotion) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = settings.haptics) {
                        AppNav(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    /** A tapped reminder opens its task's panel. */
    private fun handleOpen(intent: Intent?) {
        val id = intent?.getLongExtra(Reminders.EXTRA_OPEN_TASK, -1L) ?: -1L
        if (id >= 0) vm.requestOpen(id)
    }
}
