package io.github.markovcd.capynib.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.core.note.Photo

private const val PER_ROW = 3
private const val FILL_ALPHA = 0.07f
private const val UNKNOWN_ASPECT = 4f / 3f

/**
 * A note's photos at the top of the note or its card, as Keep lays them out: one alone spans the width
 * at its own shape, up to [maxHeight]; more go in rows of up to three squares. [onOpen] makes each one
 * tappable. Photos are decoded at about [pixels] a side.
 */
@Composable
fun PhotoGrid(photos: List<Photo>, pixels: Int, maxHeight: Dp, onOpen: ((Photo) -> Unit)?, modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return
    val single = photos.size == 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        photos.chunked(PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { photo ->
                    PhotoTile(photo, pixels, single, maxHeight, onOpen, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: Photo, pixels: Int, single: Boolean, maxHeight: Dp, onOpen: ((Photo) -> Unit)?, modifier: Modifier) {
    val bitmap = rememberPhoto(photo, pixels)
    val aspect = when {
        !single -> 1f
        bitmap != null -> bitmap.width.toFloat() / bitmap.height
        else -> UNKNOWN_ASPECT
    }
    Box(
        modifier.heightIn(max = maxHeight).aspectRatio(aspect)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = FILL_ALPHA))
            .then(if (onOpen != null) Modifier.clickable(role = Role.Image, onClickLabel = stringResource(R.string.photo_open)) { onOpen(photo) } else Modifier)
            .testTag(Tags.photo(photo.id)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else if (!photo.downloaded) {
            Icon(Icons.Outlined.CloudDownload, contentDescription = stringResource(R.string.photo_not_downloaded), modifier = Modifier.size(24.dp))
        }
    }
}

/** [photo] decoded at about [pixels] a side, or null until it is, or when it cannot be. */
@Composable
fun rememberPhoto(photo: Photo, pixels: Int): Bitmap? {
    val loader = LocalPhotos.current
    val bitmap by produceState<Bitmap?>(null, photo.id, photo.downloaded, pixels) { value = loader.load(photo, pixels) }
    return bitmap
}
