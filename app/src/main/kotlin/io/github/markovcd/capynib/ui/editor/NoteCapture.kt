package io.github.markovcd.capynib.ui.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.markovcd.capynib.R
import kotlinx.coroutines.launch
import java.io.File

/** What the record and photo buttons start: a clip, a photo with the camera app, or an image chosen on the phone. */
internal class NoteCapture(
    val record: () -> Unit,
    val takePhoto: () -> Unit,
    val takePhotoWithNote: () -> Unit,
    val choosePhoto: () -> Unit,
)

/**
 * Records through [recorder], asking for the microphone the first time, and brings photos in through [photos].
 * Each capture first calls [onCapture]. A camera opened with the note and closed without a photo calls
 * [onCameraClosedEmpty].
 */
@Composable
internal fun rememberNoteCapture(
    recorder: Recorder,
    photos: PhotoIntake,
    onCapture: () -> Unit,
    onCameraClosedEmpty: () -> Unit,
): NoteCapture {
    val context = LocalContext.current
    val beginRecording = {
        if (!recorder.start()) Toast.makeText(context, R.string.editor_microphone_failed, Toast.LENGTH_SHORT).show()
    }
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) beginRecording() }
    val record = {
        onCapture()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) beginRecording() else askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }
    val scope = rememberCoroutineScope()
    // The camera app may outlive this screen, so the file it writes into and why it opened are saved.
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraOpenedWithNote by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { took ->
        val file = shot?.let(::File) ?: return@rememberLauncherForActivityResult
        shot = null
        val kept = photos.taken(file, took)
        if (!kept && cameraOpenedWithNote) onCameraClosedEmpty()
        cameraOpenedWithNote = false
    }
    val takePhoto = {
        onCapture()
        val file = photos.newShot()
        shot = file.path
        try {
            camera.launch(photos.uriOf(file))
        } catch (e: ActivityNotFoundException) {
            shot = null
            file.delete()
            Toast.makeText(context, R.string.editor_no_camera, Toast.LENGTH_SHORT).show()
        }
    }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch { if (!photos.picked(uri)) Toast.makeText(context, R.string.editor_photo_failed, Toast.LENGTH_SHORT).show() }
        }
    }
    val choosePhoto = {
        onCapture()
        chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    return NoteCapture(
        record = record,
        takePhoto = takePhoto,
        takePhotoWithNote = {
            cameraOpenedWithNote = true
            takePhoto()
        },
        choosePhoto = choosePhoto,
    )
}
