package io.github.markovcd.capynib.capture

import io.github.markovcd.capynib.R

/** The camera widget. Its class name is saved by launchers with every placed widget, so it stays. */
class NewPhotoWidget : CaptureWidget(R.layout.widget_new_photo, 3, CaptureIntents.NEW_PHOTO)
