package io.github.markovcd.capynib.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.markovcd.capynib.photo.PhotoLoader

/** Where every photo on screen is decoded from; [CapynibRoot] provides the app's. */
val LocalPhotos = staticCompositionLocalOf<PhotoLoader> { error("no PhotoLoader provided") }
