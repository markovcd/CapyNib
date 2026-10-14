# 8. A note's place on the board is its memo's create time

## Context

Notes can be dragged to a new place on the board, as in Keep, and the place has to survive a
restart and reach other devices. Memos (v0.31) has no order field. It lists memos newest first by
create time, and lets a client set `create_time` on create and update (`display_time` is gone).

## Decision

- Each note has a `position`, in epoch seconds; the board shows the higher first, pinned notes
  ahead of the rest as before. A new note goes above every other note.
- A note's memo is written with its position as `createTime`, so the memos web app lists notes in
  the same order. A memo's `createTime` read back is the note's position.
- A move gives the moved note a position between its new neighbors, and pushes down only the notes
  below it that leave no room (`BoardContent.move`). A move is a local edit sync sends; it does not
  change `updatedAt`.
- A move is not an edit that conflicts: a note moved on one side and edited on the other takes the
  edit and the move, rather than a conflict copy. Moved on both sides, the local place wins.
- Existing notes start at their create time, which keeps them newest first.

## Consequences

- The memos web app shows a moved memo with a changed creation date.
- Positions are whole seconds, the precision memos keeps, so two notes made in the same second
  can share one; the board then orders them by id, and a move between them still lands where it
  was dropped.

## Amendment (2026-10-14)

The board's calendar puts a note on the day of its position, not of the moment this device made it.
Before a note is synced its position is the time it was made; once synced it is its memo's create
time, the same on every device. A note moved on the board moves to another day with it.
