package io.github.markovcd.capynib.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import io.github.markovcd.capynib.R

/** A one-tile home screen widget whose single button opens a new note, or a new checklist. */
abstract class CaptureWidget(private val checklist: Boolean) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        manager.updateAppWidget(widgetIds, views(context))
    }

    fun views(context: Context): RemoteViews {
        val layout = if (checklist) R.layout.widget_new_checklist else R.layout.widget_new_note
        val launch = PendingIntent.getActivity(
            context,
            if (checklist) 1 else 0,
            CaptureIntents.newNote(context, checklist),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteViews(context.packageName, layout).apply { setOnClickPendingIntent(R.id.widget_button, launch) }
    }
}
