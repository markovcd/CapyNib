package io.github.markovcd.capynib.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** The Quick Settings tile: tapping it opens a new note, after unlocking the phone if it is locked. */
class NewNoteTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::openNewNote) else openNewNote()
    }

    private fun openNewNote() {
        val intent = CaptureIntents.newNote(this, checklist = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            // The only way to open an activity from a tile before Android 14, where it still works.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
