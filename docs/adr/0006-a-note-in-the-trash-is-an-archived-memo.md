# 6. A note in the trash is an archived memo until its days there are up

## Context

Deleting a note should not lose it at once: it goes to a trash and is deleted for good some days
later (a week unless the user sets otherwise), unless it is restored first. Memos (v0.31) has no
trash; a memo is normal or archived, and deleting one is final.

## Decision

- A note in the trash keeps its row and records when it went there (`trashed_at`). The board shows
  it only on the trash shelf, and the note is read-only until restored.
- Its memo is archived while the note is in the trash. The note's own archived flag is kept
  apart, so restoring puts it back on the board or in the archive, wherever it was.
- A note that has been in the trash for the days set in settings (`Trash.DAYS`, 7 by default)
  is deleted for good: each sync, the board on opening and a change of the setting empty the
  trash of it, and sync then deletes its memo. The setting is per device, in shared preferences.
- A trashed note's memo taken out of the archive elsewhere (the memos web app, another device)
  takes the note out of the trash.

## Consequences

- Until its days are up, the server still holds the note, so a lost phone does not lose it.
- Another device and the memos web app see a trashed note as archived, and lose it when the
  device that trashed it deletes it for good.
- Delete forever, from the trash, asks first and then deletes at once, as delete did before.
