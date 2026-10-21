package dev.saikrishna.todo.ui.capture

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * v0.8 Quick Settings "Add task" tile: opens the floating capture card with the keyboard up.
 * API 34+ needs [startActivityAndCollapse] with a PendingIntent; 33 uses the Intent overload.
 * On a locked device it asks to unlock first.
 */
class AddTaskTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Add task"
            if (Build.VERSION.SDK_INT >= 29) subtitle = null
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { open() } else open()
    }

    private fun open() {
        val intent = captureIntent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 10, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val ACTION_CAPTURE = "dev.saikrishna.todo.CAPTURE"
        fun captureIntent(context: android.content.Context): Intent =
            Intent(context, CaptureActivity::class.java).setAction(ACTION_CAPTURE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
