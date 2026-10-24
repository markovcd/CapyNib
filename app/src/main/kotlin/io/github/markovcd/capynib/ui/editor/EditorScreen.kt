package io.github.markovcd.capynib.ui.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.audio.Playback
import io.github.markovcd.capynib.core.editor.NoteEditor
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.core.note.Opening
import io.github.markovcd.capynib.ui.ColorPalette
import io.github.markovcd.capynib.ui.PhotoGrid
import io.github.markovcd.capynib.ui.PlainTextField
import io.github.markovcd.capynib.ui.RecordingPlayer
import io.github.markovcd.capynib.ui.clipTime
import io.github.markovcd.capynib.ui.ConfirmDeleteForever
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.theme.NoteColors
import kotlinx.coroutines.delay

internal const val RULE_ALPHA = 0.12f
private const val ELAPSED_TICK_MILLIS = 250L
private const val EDITOR_PHOTO_PIXELS = 1080

/**
 * The open note. Leaving it (back, archive, delete) calls [onClose]. A note in the trash is read-only until restored.
 * It opens as [opening] says: ready to type puts the body, or a checklist's first item, in focus with the keyboard up.
 * The record button records a clip into the note through [recorder], asking for the microphone the first time;
 * opening to record presses it as the note opens. The photo button takes a photo with the camera app or copies in a
 * chosen image through [photos]; opening to take a photo opens the camera with the note, and a note left blank when
 * it is closed without a photo is left too. Once a recording or the camera has started, [onOpened] is told so it
 * starts once. Photos sit at the top of the note, and tapping one shows them full screen.
 */
@Composable
fun EditorScreen(
    editor: NoteEditor,
    recorder: Recorder,
    photos: PhotoIntake,
    playback: Playback,
    clock: () -> Long,
    opening: Opening,
    onOpened: () -> Unit,
    onClose: () -> Unit,
) {
    val note by editor.note.collectAsStateWithLifecycle()
    val recordingSince by recorder.since.collectAsStateWithLifecycle()
    val playing by playback.playing.collectAsStateWithLifecycle()
    // Every way out of the note stops a clip that is recording, then does [last] to the note.
    fun leave(last: () -> Unit = { editor.close() }) {
        recorder.stop()
        last()
        onClose()
    }
    BackHandler { leave() }
    // Opening another note leaves this one without [leave]; a rotation leaves the clip recording.
    val activity = LocalActivity.current
    DisposableEffect(recorder) {
        onDispose { if (activity?.isChangingConfigurations != true) recorder.stop() }
    }
    val checklist = Checklist.isChecklist(note.body)
    val editable = !note.inTrash
    var confirmingDelete by remember { mutableStateOf(false) }
    var showingColors by remember { mutableStateOf(false) }
    BackHandler(enabled = showingColors) { showingColors = false }
    val focus = LocalFocusManager.current
    val capture = rememberNoteCapture(
        recorder,
        photos,
        onCapture = {
            focus.clearFocus()
            showingColors = false
        },
        onCameraClosedEmpty = { if (editor.note.value.isBlank) leave() },
    )
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        when (opening) {
            Opening.Record -> if (editable) capture.record()
            Opening.Photo -> if (editable) capture.takePhotoWithNote()
            Opening.Read, Opening.Type -> return@LaunchedEffect
        }
        onOpened()
    }
    val startTyping = opening == Opening.Type
    if (confirmingDelete) {
        ConfirmDeleteForever(
            count = 1,
            onConfirm = {
                confirmingDelete = false
                leave(editor::deleteForever)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
    val fill = NoteColors.background(note.color)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = fill,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                EditorTopBar(
                    note,
                    onBack = { leave() },
                    onRestore = editor::restore,
                    onDeleteForever = { confirmingDelete = true },
                    onPin = editor::togglePinned,
                    onArchive = {
                        leave {
                            editor.setArchived(!note.archived)
                            editor.close()
                        }
                    },
                    onTrash = { leave(editor::trash) },
                )
            },
            bottomBar = {
                if (editable) {
                    EditorBottomBar(
                        selected = note.color,
                        checklist = checklist,
                        onColor = editor::setColor,
                        showingColors = showingColors,
                        onPalette = {
                            if (!showingColors) focus.clearFocus()
                            showingColors = !showingColors
                        },
                        onToggleCheckboxes = { if (checklist) editor.hideCheckboxes() else editor.showCheckboxes() },
                        recordingSince = recordingSince,
                        clock = clock,
                        onRecord = capture.record,
                        onStopRecording = recorder::stop,
                        onTakePhoto = capture.takePhoto,
                        onChoosePhoto = capture.choosePhoto,
                    )
                }
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())
                    .onFocusEvent { if (it.hasFocus) showingColors = false },
            ) {
                PhotoGrid(note.photos, EDITOR_PHOTO_PIXELS, maxHeight = 480.dp, onOpen = { viewing = it.id })
                PlainTextField(
                    value = note.title,
                    onValueChange = editor::setTitle,
                    placeholder = stringResource(R.string.editor_title_hint),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp).testTag(Tags.TITLE),
                    readOnly = !editable,
                )
                if (checklist) {
                    ChecklistEditor(note.body, editor, editable, startTyping, fill)
                } else {
                    val body = remember { FocusRequester() }
                    LaunchedEffect(Unit) { if (startTyping && editable) body.requestFocus() }
                    PlainTextField(
                        value = note.body,
                        onValueChange = editor::setBody,
                        placeholder = stringResource(R.string.editor_body_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).focusRequester(body).testTag(Tags.BODY),
                        readOnly = !editable,
                    )
                }
                // Clips sit under the text, oldest first, as in Keep.
                note.recordings.forEach { recording ->
                    key(recording.id) {
                        RecordingPlayer(
                            recording,
                            playing,
                            onPlay = { playback.toggle(recording) },
                            onRemove = if (editable) {
                                {
                                    if (playing?.id == recording.id) playback.stop()
                                    editor.removeRecording(recording.id)
                                }
                            } else {
                                null
                            },
                            compact = false,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        val shown = viewing
        if (shown != null && note.photos.isNotEmpty()) {
            PhotoViewer(
                note.photos,
                first = shown,
                onRemove = if (editable) {
                    { photo ->
                        if (note.photos.size == 1) viewing = null
                        editor.removePhoto(photo.id)
                    }
                } else {
                    null
                },
                onClose = { viewing = null },
            )
        }
    }
}

/**
 * The note's tools: the checkbox toggle, the photo and record buttons and, as in Keep, the colors folded behind a
 * palette button until it is tapped. While a clip records, the tools give way to how long it has run and a stop button.
 */
@Composable
private fun EditorBottomBar(
    selected: NoteColor,
    checklist: Boolean,
    onColor: (NoteColor) -> Unit,
    showingColors: Boolean,
    onPalette: () -> Unit,
    onToggleCheckboxes: () -> Unit,
    recordingSince: Long?,
    clock: () -> Long,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        HorizontalDivider(color = ink.copy(alpha = RULE_ALPHA))
        if (recordingSince != null) {
            RecordingBar(recordingSince, clock, onStopRecording)
            return@Column
        }
        AnimatedVisibility(showingColors) {
            ColorPalette(selected, onColor, Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp))
        }
        Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleCheckboxes, modifier = Modifier.testTag(Tags.CHECKBOXES)) {
                Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(22.dp), tint = ink)
                Text(
                    stringResource(if (checklist) R.string.editor_hide_checkboxes else R.string.editor_show_checkboxes),
                    Modifier.padding(start = 10.dp),
                    color = ink,
                )
            }
            Spacer(Modifier.weight(1f))
            PhotoButton(ink, onTakePhoto, onChoosePhoto)
            IconButton(onClick = onRecord, modifier = Modifier.testTag(Tags.RECORD)) {
                Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.editor_record), tint = ink)
            }
            IconButton(onClick = onPalette, modifier = Modifier.testTag(Tags.PALETTE)) {
                Icon(Icons.Outlined.Palette, contentDescription = stringResource(R.string.editor_colors), tint = ink)
            }
        }
    }
}

/** The photo button and its menu: take one with the camera app, or choose an image on the phone. */
@Composable
private fun PhotoButton(ink: Color, onTakePhoto: () -> Unit, onChoosePhoto: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(Tags.ADD_PHOTO)) {
            Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.editor_add_photo), tint = ink)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_take_photo)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                onClick = {
                    open = false
                    onTakePhoto()
                },
                modifier = Modifier.testTag(Tags.TAKE_PHOTO),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_choose_photo)) },
                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                onClick = {
                    open = false
                    onChoosePhoto()
                },
                modifier = Modifier.testTag(Tags.CHOOSE_PHOTO),
            )
        }
    }
}

/** A red dot, how long the clip has been recording, and the button that ends it. */
@Composable
private fun RecordingBar(since: Long, clock: () -> Long, onStop: () -> Unit) {
    val elapsed by produceState(clock() - since, since) {
        while (true) {
            value = clock() - since
            delay(ELAPSED_TICK_MILLIS)
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        Text(
            stringResource(R.string.editor_recording, clipTime(elapsed)),
            Modifier.padding(start = 12.dp).weight(1f).testTag(Tags.RECORDING_ELAPSED),
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onClick = onStop, modifier = Modifier.testTag(Tags.STOP_RECORDING)) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.editor_stop_recording), Modifier.padding(start = 8.dp))
        }
    }
}
