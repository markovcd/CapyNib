package io.github.markovcd.capynib.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import io.github.markovcd.capynib.R

/**
 * A one-tile home screen widget whose single button, drawn by [layout], opens a new note for [action].
 * [requestCode] keeps each widget's tap apart from the others'.
 */
abstract class CaptureWidget(private val layout: Int, private val requestCode: Int, private val action: String) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        manager.updateAppWidget(widgetIds, views(context))
    }

    fun views(context: Context): RemoteViews {
        val launch = PendingIntent.getActivity(
            context,
            requestCode,
            CaptureIntents.newNote(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteViews(context.packageName, layout).apply { setOnClickPendingIntent(R.id.widget_button, launch) }
    }
}
