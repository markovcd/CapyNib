# 1. The app is native Kotlin and Compose over a plain Kotlin core

## Context

Capynib is an Android-only notes app whose center is typing into colored cards and having it sync
as you type. Every feature has to be testable by an agent without a phone or an emulator.

## Decision

The app is native Kotlin with Jetpack Compose and Material 3. Everything that is not drawing lives
in `core`, a plain Kotlin JVM module: the note model, the memo format, the SQLite store
(SQLDelight), the memos client (OkHttp), sync, and the logic behind each screen (`NoteEditor`,
`BoardContent`, `SyncScheduler`). The Android module wires them to screens, WorkManager and the
keystore. A `cli` module drives the same core from a terminal.

SQLDelight rather than Room because its generated code is plain Kotlin: the same queries run on the
JVM driver in tests and the command line, and on the Android driver in the app.

## Consequences

- Most tests run on a plain JVM in seconds, including sync against a real memos server in Docker.
- Compose UI is tested with Robolectric; there are no instrumented tests to run on a device.
- Cross-platform frameworks (Flutter, React Native, Kotlin Multiplatform UI) were not chosen: the
  app targets one platform, and native Compose gives the most Keep-like feel for the least code.
