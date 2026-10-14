package io.github.markovcd.capynib.ui

import java.time.LocalDate

/** Stable names tests find controls by; never the caption or icon they show. */
object Tags {
    const val SEARCH = "search"
    const val NEW_NOTE = "new-note"
    const val NEW_LIST = "new-list"
    const val SETTINGS = "settings"
    const val ARCHIVE_TOGGLE = "archive-toggle"
    const val TRASH_TOGGLE = "trash-toggle"
    const val CALENDAR_TOGGLE = "calendar-toggle"
    const val CALENDAR = "calendar"
    const val MONTH = "month"
    const val PREVIOUS_MONTH = "previous-month"
    const val NEXT_MONTH = "next-month"
    const val DAY_EMPTY = "day-empty"
    const val TRASH_NOTICE = "trash-notice"
    const val SYNC_STATUS = "sync-status"
    const val EMPTY_BOARD = "empty-board"
    const val PINNED = "pinned"
    const val OTHERS = "others"
    const val BOARD = "board"
    const val SELECTION_BAR = "selection-bar"
    const val CLOSE_SELECTION = "close-selection"
    const val SELECTED_COUNT = "selected-count"
    const val PIN_SELECTED = "pin-selected"
    const val COLOR_SELECTED = "color-selected"
    const val ARCHIVE_SELECTED = "archive-selected"
    const val TRASH_SELECTED = "trash-selected"
    const val RESTORE_SELECTED = "restore-selected"
    const val DELETE_FOREVER_SELECTED = "delete-forever-selected"
    const val UNDO = "undo"

    fun card(id: String) = "card-$id"

    fun cardTags(id: String) = "card-tags-$id"

    fun day(date: LocalDate) = "day-$date"

    fun dayMarks(date: LocalDate) = "day-marks-$date"

    const val TAG_BAR = "tag-bar"

    fun tag(name: String) = "tag-$name"

    const val BACK = "back"
    const val PIN = "pin"
    const val ARCHIVE = "archive"
    const val DELETE = "delete"
    const val RESTORE = "restore"
    const val DELETE_FOREVER = "delete-forever"
    const val CONFIRM_DELETE_FOREVER = "confirm-delete-forever"
    const val CANCEL_DELETE_FOREVER = "cancel-delete-forever"
    const val TITLE = "title"
    const val BODY = "body"
    const val CHECKBOXES = "checkboxes"
    const val ADD_ITEM = "add-item"
    const val PALETTE = "palette"
    const val RECORD = "record"
    const val STOP_RECORDING = "stop-recording"
    const val RECORDING_ELAPSED = "recording-elapsed"
    const val ADD_PHOTO = "add-photo"
    const val TAKE_PHOTO = "take-photo"
    const val CHOOSE_PHOTO = "choose-photo"
    const val PHOTO_VIEWER = "photo-viewer"
    const val CLOSE_PHOTO = "close-photo"
    const val REMOVE_PHOTO = "remove-photo"

    fun recording(id: String) = "recording-$id"

    fun recordingPlay(id: String) = "recording-play-$id"

    fun recordingTime(id: String) = "recording-time-$id"

    fun recordingRemove(id: String) = "recording-remove-$id"

    fun photo(id: String) = "photo-$id"

    fun viewedPhoto(id: String) = "viewed-photo-$id"

    fun item(index: Int) = "item-$index"

    fun itemCheck(index: Int) = "item-check-$index"

    fun itemRemove(index: Int) = "item-remove-$index"

    fun itemDrag(index: Int) = "item-drag-$index"

    fun color(key: String) = "color-$key"

    const val SERVER = "server"
    const val TOKEN = "token"
    const val SIGN_IN = "sign-in"
    const val REDIRECT_URI = "redirect-uri"
    const val CONNECT = "connect"
    const val DISCONNECT = "disconnect"
    const val CHECK_SERVER = "check-server"
    const val SETTINGS_MESSAGE = "settings-message"
    const val TRASH_DAYS = "trash-days"
}
