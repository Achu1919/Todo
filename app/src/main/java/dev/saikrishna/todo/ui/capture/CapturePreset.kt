package dev.saikrishna.todo.ui.capture

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable

/**
 * v0.12 widget presets for the capture card: a list to file into, "due today", and start
 * listening at once. Carried as intent extras; the data URI makes each preset a distinct
 * PendingIntent (otherwise the launcher would hand every widget button the same one).
 */
@Immutable
data class CapturePreset(val listId: Long? = null, val dueToday: Boolean = false, val voice: Boolean = false) {
    val isDefault: Boolean get() = this == CapturePreset()

    /** `todo://capture?list=2&due=today&voice=1` (only the set parts). */
    fun uri(): Uri {
        val b = Uri.Builder().scheme("todo").authority("capture")
        listId?.let { b.appendQueryParameter("list", it.toString()) }
        if (dueToday) b.appendQueryParameter("due", "today")
        if (voice) b.appendQueryParameter("voice", "1")
        return b.build()
    }

    fun applyTo(intent: Intent): Intent {
        if (isDefault) return intent
        intent.data = uri()
        listId?.let { intent.putExtra(EXTRA_LIST, it) }
        if (dueToday) intent.putExtra(EXTRA_DUE_TODAY, true)
        if (voice) intent.putExtra(EXTRA_VOICE, true)
        return intent
    }

    companion object {
        const val EXTRA_LIST = "dev.saikrishna.todo.extra.CAPTURE_LIST"
        const val EXTRA_DUE_TODAY = "dev.saikrishna.todo.extra.CAPTURE_DUE_TODAY"
        const val EXTRA_VOICE = "dev.saikrishna.todo.extra.CAPTURE_VOICE"

        /** Read back from a capture intent (shares and the plain tile give the default). */
        fun of(intent: Intent?): CapturePreset {
            if (intent == null || intent.action == Intent.ACTION_SEND) return CapturePreset()
            val list = intent.getLongExtra(EXTRA_LIST, -1L).takeIf { it > 0 }
            return CapturePreset(
                listId = list,
                dueToday = intent.getBooleanExtra(EXTRA_DUE_TODAY, false),
                voice = intent.getBooleanExtra(EXTRA_VOICE, false),
            )
        }
    }
}
