# 3. The device is the source of truth, and a conflict keeps both versions

## Context

Notes must be editable with no network or no server set up, and sync as the user types when there
is one. Memos has no change feed or push; its timestamps have one-second resolution.

## Decision

- Every edit is written to the local SQLite store first. Each row counts local edits (`version`)
  and remembers the version and the memo content last agreed with the server.
- A sync lists all of the user's memos, then pulls and pushes:
  - a memo that differs from what was last agreed replaces a note with no local edits;
  - when both sides changed, the local edit is pushed and the server's version is kept as a new
    local note (a conflict copy), so nothing typed anywhere is lost;
  - a memo gone from the server removes an unedited note, and an edited one is created again;
  - a note deleted here is deleted there.
- Notes are created on the server under their own id (`memoId`), so a create retried after a lost
  reply finds the memo instead of duplicating it.
- `SyncScheduler` syncs about a second after typing starts and once per second while it continues,
  retries failures with growing delays, and never retries a refused token.

## Consequences

- Comparing content rather than timestamps makes change detection exact, at the cost of listing
  every memo on each sync. That is fine for a personal notes collection; paging by `update_time`
  is the next step if it ever is not.
