package dev.saikrishna.todo.ui.capture

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.AppSettings
import dev.saikrishna.todo.data.NewTask
import dev.saikrishna.todo.data.Priority
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.FocusMode
import dev.saikrishna.todo.domain.ShareCapture
import dev.saikrishna.todo.ui.motion.ProvideHaptics
import dev.saikrishna.todo.ui.motion.ProvideMotion
import dev.saikrishna.todo.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * v0.8 quick capture: a translucent activity that floats a glass card over whatever is on screen
 * (the sharing app, or the home screen for the Quick Settings tile / widget "+"). It never opens
 * the full app. ACTION_SEND text/plain (+ EXTRA_SUBJECT) is prefilled through [ShareCapture];
 * the tile and the widget start it with an empty field and the keyboard up.
 *
 * Where the system allows it, the window blurs what is behind it (cross-window blur, API 31+);
 * otherwise the card's own scrim dims it.
 */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        val blur = Build.VERSION.SDK_INT >= 31 && windowManager.isCrossWindowBlurEnabled
        if (blur) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = (20 * resources.displayMetrics.density).toInt() }
        }
        val draft = draftOf(intent)
        val preset = CapturePreset.of(intent)
        // v0.12 "Voice" preset: listening starts at once, so the keyboard stays down.
        if (preset.voice) {
            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
        }
        val app = application as TodoApp

        setContent {
            val stored by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val settings = stored ?: AppSettings()
            val lists by app.repository.lists.collectAsStateWithLifecycle(initialValue = TaskList.Defaults)
            val focus by app.settings.focus.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            val dark = settings.theme.isDark(isSystemInDarkTheme())
            ProvideMotion(userPrefReduceMotion = settings.reduceMotion) {
                AppTheme(darkTheme = dark) {
                    ProvideHaptics(userEnabled = settings.haptics) {
                        CaptureScreen(
                            draft = draft,
                            lists = lists,
                            defaultList = defaultListFor(focus?.let {
                                dev.saikrishna.todo.domain.Focus.effective(it.manual, it.manualAt, it.schedule, System.currentTimeMillis(), app.repository.zone())
                            }),
                            windowBlur = blur,
                            preset = preset,
                            onSave = { parsed, notes, listId ->
                                scope.launch {
                                    app.repository.add(
                                        NewTask(
                                            title = parsed.title,
                                            dueDay = parsed.dueDay?.toEpochDay(), // a "due today" preset is already applied
                                            dueMinute = parsed.dueMinute,
                                            priority = parsed.priority ?: Priority.None,
                                            repeat = parsed.repeat,
                                            listId = listId,
                                            tags = parsed.tags,
                                            notes = notes,
                                        ),
                                    )
                                }
                            },
                            onSaveDraft = { d, notes, listId ->
                                scope.launch { app.repository.add(d.toNewTask(listId, extraNotes = notes)) }
                            },
                            onFinished = ::close,
                        )
                    }
                }
            }
        }
    }

    private fun close() {
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        finish()
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        /** Shared text → draft; anything else (tile, widget) → empty. */
        fun draftOf(intent: Intent?): ShareCapture.Draft {
            if (intent?.action != Intent.ACTION_SEND) return ShareCapture.Draft("", "", emptyList())
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE)
            return ShareCapture.parse(text, subject)
        }

        fun defaultListFor(mode: FocusMode?): Long = if (mode == FocusMode.Work) TaskList.WORK else TaskList.PERSONAL
    }
}

