package io.github.markovcd.capynib.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import io.github.markovcd.capynib.R

/** The home screen widget: a New note button and a New checklist button. */
class NewNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        manager.updateAppWidget(widgetIds, views(context))
    }

    companion object {
        fun views(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_new_note).apply {
            setOnClickPendingIntent(R.id.widget_new_note, launch(context, checklist = false))
            setOnClickPendingIntent(R.id.widget_new_checklist, launch(context, checklist = true))
        }

        private fun launch(context: Context, checklist: Boolean): PendingIntent = PendingIntent.getActivity(
            context,
            if (checklist) 1 else 0,
            CaptureIntents.newNote(context, checklist),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
