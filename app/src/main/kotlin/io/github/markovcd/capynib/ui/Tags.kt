package io.github.markovcd.capynib.ui

/** Stable names tests find controls by; never the caption or icon they show. */
object Tags {
    const val SEARCH = "search"
    const val NEW_NOTE = "new-note"
    const val NEW_LIST = "new-list"
    const val SETTINGS = "settings"
    const val ARCHIVE_TOGGLE = "archive-toggle"
    const val TRASH_TOGGLE = "trash-toggle"
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

    fun item(index: Int) = "item-$index"

    fun itemCheck(index: Int) = "item-check-$index"

    fun itemRemove(index: Int) = "item-remove-$index"

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
