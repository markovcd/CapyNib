package io.github.markovcd.capynib.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.note.Photo
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.rememberPhoto

private const val FULL_PIXELS = 2048

/**
 * The note's [photos] full screen on black, opened on [first]; a swipe goes to the next. [onRemove]
 * adds a button that removes the one showing; back or the close button calls [onClose].
 */
@Composable
fun PhotoViewer(photos: List<Photo>, first: String, onRemove: ((Photo) -> Unit)?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = photos.indexOfFirst { it.id == first }.coerceAtLeast(0)) { photos.size }
    // A surface takes every touch, so nothing reaches the note beneath.
    Surface(Modifier.fillMaxSize().testTag(Tags.PHOTO_VIEWER), color = Color.Black, contentColor = Color.White) {
        Box {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { photos[it].id }) { page ->
                val photo = photos[page]
                val bitmap = rememberPhoto(photo, FULL_PIXELS)
                Box(Modifier.fillMaxSize().testTag(Tags.viewedPhoto(photo.id)), contentAlignment = Alignment.Center) {
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.testTag(Tags.CLOSE_PHOTO)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.photo_close))
                }
                Spacer(Modifier.weight(1f))
                if (onRemove != null) {
                    IconButton(
                        onClick = { photos.getOrNull(pager.currentPage)?.let(onRemove) },
                        modifier = Modifier.testTag(Tags.REMOVE_PHOTO),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.photo_remove))
                    }
                }
            }
        }
    }
}
