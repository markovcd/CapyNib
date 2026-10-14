package io.github.markovcd.capynib.capture

import io.github.markovcd.capynib.R

/** The microphone widget. Its class name is saved by launchers with every placed widget, so it stays. */
class NewAudioNoteWidget : CaptureWidget(R.layout.widget_new_audio_note, 2, CaptureIntents.NEW_AUDIO_NOTE)
