# 6. A note in the trash is an archived memo until it has been there a week

## Context

Deleting a note should not lose it at once: it goes to a trash and is deleted for good a week
later, unless it is restored first. Memos (v0.31) has no trash; a memo is normal or archived, and
deleting one is final.

## Decision

- A note in the trash keeps its row and records when it went there (`trashed_at`). The board shows
  it only on the trash shelf, and the note is read-only until restored.
- Its memo is archived while the note is in the trash. The note's own archived flag is kept
  apart, so restoring puts it back on the board or in the archive, wherever it was.
- A note that has been in the trash for `Trash.KEPT_FOR` (seven days) is deleted for good: each
  sync, and the board on opening, empty the trash of it, and sync then deletes its memo.
- A trashed note's memo taken out of the archive elsewhere (the memos web app, another device)
  takes the note out of the trash.

## Consequences

- Until the week is up, the server still holds the note, so a lost phone does not lose it.
- Another device and the memos web app see a trashed note as archived, and lose it when the
  device that trashed it deletes it for good.
- Delete forever, from the trash, asks first and then deletes at once, as delete did before.
