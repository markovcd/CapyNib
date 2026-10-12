package io.github.markovcd.quicknib.ui

/** Stable names tests find controls by; never the caption or icon they show. */
object Tags {
    const val SEARCH = "search"
    const val NEW_NOTE = "new-note"
    const val NEW_LIST = "new-list"
    const val SETTINGS = "settings"
    const val ARCHIVE_TOGGLE = "archive-toggle"
    const val SYNC_STATUS = "sync-status"
    const val EMPTY_BOARD = "empty-board"

    fun card(id: String) = "card-$id"

    const val BACK = "back"
    const val PIN = "pin"
    const val ARCHIVE = "archive"
    const val DELETE = "delete"
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
    const val CONNECT = "connect"
    const val DISCONNECT = "disconnect"
    const val SYNC_NOW = "sync-now"
    const val SETTINGS_MESSAGE = "settings-message"
}
