package io.github.markovcd.capynib.ui.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markovcd.capynib.R
import io.github.markovcd.capynib.audio.Playback
import io.github.markovcd.capynib.core.editor.NoteEditor
import io.github.markovcd.capynib.core.note.Checklist
import io.github.markovcd.capynib.core.note.ChecklistLine
import io.github.markovcd.capynib.core.note.NoteColor
import io.github.markovcd.capynib.ui.ColorPalette
import io.github.markovcd.capynib.ui.PhotoGrid
import io.github.markovcd.capynib.ui.PlainTextField
import io.github.markovcd.capynib.ui.RecordingPlayer
import io.github.markovcd.capynib.ui.clipTime
import io.github.markovcd.capynib.ui.ConfirmDeleteForever
import io.github.markovcd.capynib.ui.Tags
import io.github.markovcd.capynib.ui.theme.NoteColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val TICKED_ALPHA = 0.62f
private const val RULE_ALPHA = 0.12f
private const val ELAPSED_TICK_MILLIS = 250L
private const val EDITOR_PHOTO_PIXELS = 1080

/**
 * The open note. Leaving it (back, archive, delete) calls [onClose]. A note in the trash is read-only until restored.
 * With [startTyping] the body, or a checklist's first item, takes focus and the keyboard comes up.
 * The record button records a clip into the note through [recorder], asking for the microphone the first time;
 * with [startRecording] it is pressed as the note opens, and [onRecordingStarted] is told so it is pressed once.
 * The photo button takes a photo with the camera app or copies in a chosen image through [photos]; with
 * [startCamera] the camera opens with the note, and a note left blank when it is closed without a photo is
 * left too. Photos sit at the top of the note, and tapping one shows them full screen.
 */
@Composable
fun EditorScreen(
    editor: NoteEditor,
    recorder: Recorder,
    photos: PhotoIntake,
    playback: Playback,
    clock: () -> Long,
    startTyping: Boolean,
    startRecording: Boolean,
    onRecordingStarted: () -> Unit,
    startCamera: Boolean,
    onCameraStarted: () -> Unit,
    onClose: () -> Unit,
) {
    val note by editor.note.collectAsStateWithLifecycle()
    val recordingSince by recorder.since.collectAsStateWithLifecycle()
    val playing by playback.playing.collectAsStateWithLifecycle()
    val leave = {
        recorder.stop()
        editor.close()
        onClose()
    }
    BackHandler(onBack = leave)
    val checklist = Checklist.isChecklist(note.body)
    val editable = !note.inTrash
    var confirmingDelete by remember { mutableStateOf(false) }
    var showingColors by remember { mutableStateOf(false) }
    BackHandler(enabled = showingColors) { showingColors = false }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val beginRecording = {
        if (!recorder.start()) Toast.makeText(context, R.string.editor_microphone_failed, Toast.LENGTH_SHORT).show()
    }
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) beginRecording() }
    val record = {
        focus.clearFocus()
        showingColors = false
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) beginRecording() else askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) {
        if (startRecording && editable) record()
        if (startRecording) onRecordingStarted()
    }
    val scope = rememberCoroutineScope()
    // The camera app may outlive this screen, so the file it writes into and why it opened are saved.
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraOpenedWithNote by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { took ->
        val file = shot?.let(::File) ?: return@rememberLauncherForActivityResult
        shot = null
        val kept = photos.taken(file, took)
        if (!kept && cameraOpenedWithNote && editor.note.value.isBlank) leave()
        cameraOpenedWithNote = false
    }
    val takePhoto = {
        focus.clearFocus()
        showingColors = false
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
        focus.clearFocus()
        showingColors = false
        chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(Unit) {
        if (startCamera && editable) {
            cameraOpenedWithNote = true
            takePhoto()
        }
        if (startCamera) onCameraStarted()
    }
    if (confirmingDelete) {
        ConfirmDeleteForever(
            count = 1,
            onConfirm = {
                confirmingDelete = false
                editor.deleteForever()
                onClose()
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
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = leave, modifier = Modifier.testTag(Tags.BACK)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                        }
                    },
                    actions = {
                        if (!editable) {
                            IconButton(onClick = editor::restore, modifier = Modifier.testTag(Tags.RESTORE)) {
                                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = stringResource(R.string.editor_restore))
                            }
                            IconButton(
                                onClick = { confirmingDelete = true },
                                modifier = Modifier.testTag(Tags.DELETE_FOREVER),
                            ) {
                                Icon(Icons.Outlined.DeleteForever, contentDescription = stringResource(R.string.editor_delete_forever))
                            }
                        } else {
                            IconButton(onClick = editor::togglePinned, modifier = Modifier.testTag(Tags.PIN)) {
                                if (note.pinned) {
                                    Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.editor_unpin))
                                } else {
                                    Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.editor_pin))
                                }
                            }
                            IconButton(
                                onClick = {
                                    editor.setArchived(!note.archived)
                                    leave()
                                },
                                modifier = Modifier.testTag(Tags.ARCHIVE),
                            ) {
                                if (note.archived) {
                                    Icon(Icons.Outlined.Unarchive, contentDescription = stringResource(R.string.editor_unarchive))
                                } else {
                                    Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.editor_archive))
                                }
                            }
                            IconButton(
                                onClick = {
                                    editor.trash()
                                    onClose()
                                },
                                modifier = Modifier.testTag(Tags.DELETE),
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.editor_delete))
                            }
                        }
                    },
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
                        onRecord = record,
                        onStopRecording = recorder::stop,
                        onTakePhoto = takePhoto,
                        onChoosePhoto = choosePhoto,
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

/** The open lines (unticked items and text) and the ticked items, each a list of line indexes in body order. */
private fun sections(body: String): Pair<List<Int>, List<Int>> {
    val (checked, open) = Checklist.parse(body).withIndex().partition { (it.value as? ChecklistLine.Item)?.checked == true }
    return open.map { it.index } to checked.map { it.index }
}

/**
 * Unticked items and text in their order, then the ticked items under a count, as Keep does.
 * A line dragged by its handle swaps with a neighbor of its own section once it is past half of it.
 */
@Composable
private fun ChecklistEditor(body: String, editor: NoteEditor, editable: Boolean, startTyping: Boolean, fill: Color) {
    val lines = Checklist.parse(body)
    val (open, checked) = sections(body)
    var focus by remember { mutableStateOf(open.firstOrNull()?.takeIf { startTyping && editable }) }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableMapOf<Int, Int>() }
    val focusManager = LocalFocusManager.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val onDragStart = { index: Int ->
        focusManager.clearFocus()
        dragged = index
        dragOffset = 0f
    }
    val onDrag = { delta: Float ->
        dragOffset += delta
        // Reads the body from the editor: several moves can land before the next recomposition.
        while (true) {
            val from = dragged ?: break
            val section = sections(editor.note.value.body).toList().first { from in it }
            val position = section.indexOf(from)
            val below = section.getOrNull(position + 1)
            val above = section.getOrNull(position - 1)
            val target = when {
                below != null && dragOffset > heights.getOrDefault(below, 0) / 2f -> below
                above != null && dragOffset < -heights.getOrDefault(above, 0) / 2f -> above
                else -> break
            }
            val height = heights.getOrDefault(target, 0)
            editor.moveItem(from, target)
            dragged = target
            dragOffset += if (target > from) -height else height
        }
    }
    val onDragEnd = { dragged = null }

    val itemRow: @Composable (Int) -> Unit = { index ->
        key(index) {
            ChecklistRow(
                index, lines[index], editor, editable, focus, { focus = it }, onDragStart, onDrag, onDragEnd,
                Modifier.onSizeChanged { heights[index] = it.height }.then(
                    if (dragged == index) {
                        Modifier.zIndex(1f).graphicsLayer {
                            translationY = dragOffset
                            shadowElevation = 8.dp.toPx()
                        }.background(fill)
                    } else {
                        Modifier
                    },
                ),
            )
        }
    }

    open.forEach { itemRow(it) }
    if (editable) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { focus = editor.addItemAfter(open.lastOrNull() ?: -1) }
                .padding(horizontal = 18.dp).testTag(Tags.ADD_ITEM),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(22.dp), tint = muted)
            Text(stringResource(R.string.editor_add_item), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
    }
    if (checked.isNotEmpty()) {
        HorizontalDivider(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RULE_ALPHA),
        )
        Text(
            pluralStringResource(R.plurals.editor_checked_items, checked.size, checked.size),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = muted,
        )
        checked.forEach { itemRow(it) }
    }
}

@Composable
private fun ChecklistRow(
    index: Int,
    line: ChecklistLine,
    editor: NoteEditor,
    editable: Boolean,
    focus: Int?,
    onFocus: (Int?) -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus) {
        if (focus == index) {
            requester.requestFocus()
            onFocus(null)
        }
    }
    // The gesture outlives this row's line moving away from under the finger, so it reads the latest of both.
    val currentIndex by rememberUpdatedState(index)
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val ticked = (line as? ChecklistLine.Item)?.checked == true
    Row(modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (editable) {
            Box(
                Modifier.size(width = 28.dp, height = 44.dp).testTag(Tags.itemDrag(index)).pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { currentDragStart(currentIndex) },
                        onDragEnd = { currentDragEnd() },
                        onDragCancel = { currentDragEnd() },
                    ) { change, amount ->
                        change.consume()
                        currentDrag(amount)
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = stringResource(R.string.editor_move_item),
                    modifier = Modifier.size(20.dp).alpha(0.7f),
                )
            }
        }
        if (line is ChecklistLine.Item) {
            ItemCheckbox(line.checked, editable, onToggle = { editor.toggleItem(index) }, modifier = Modifier.testTag(Tags.itemCheck(index)))
        } else {
            Box(Modifier.size(44.dp))
        }
        PlainTextField(
            value = line.text,
            onValueChange = { text ->
                editor.setItemText(index, text)
                if ('\n' in text) onFocus(index + text.count { it == '\n' })
            },
            placeholder = stringResource(R.string.editor_item_hint),
            style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (ticked) TextDecoration.LineThrough else null),
            modifier = Modifier.weight(1f).padding(start = 4.dp).focusRequester(requester).testTag(Tags.item(index))
                .then(if (ticked) Modifier.alpha(TICKED_ALPHA) else Modifier),
            readOnly = !editable,
        )
        if (editable) {
            IconButton(onClick = { editor.removeItem(index) }, modifier = Modifier.size(44.dp).testTag(Tags.itemRemove(index))) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.editor_remove_item),
                    modifier = Modifier.size(18.dp).alpha(0.7f),
                )
            }
        }
    }
}

/** A 20 dp box with soft corners, filled with the accent when ticked. */
@Composable
private fun ItemCheckbox(checked: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.size(44.dp).toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        val box = Modifier.size(20.dp)
        if (checked) {
            Box(box.background(colors.primary, shape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onPrimary)
            }
        } else {
            Box(box.border(2.dp, colors.onSurfaceVariant, shape))
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
