# Glossary

One word for each thing, and the same word everywhere a person reads: the UI,
the docs, ADRs, comments, test names and commit messages.

- **Say** is the word. Use it in prose, whatever the code calls the thing.
- **Code** is the name in source. Where it differs from **Say**, it differs
  because renaming it would break something persisted or public (a file
  format, an API). A comment on it still uses **Say**.
- **Not** lists words that must not stand for the thing, and why where it
  helps.

| Say | Means | Code | Not |
|---|---|---|---|
| **note** | One card the user writes: a title, a body and a color. | `Note` | sticker, card (the card is how a note is drawn), memo (that is the server's copy) |
| **body** | A note's text below the title, plain text with checklist items in it. | `Note.body` | content (that is the memo's Markdown) |
| **checklist item** | A body line with a checkbox, stored as `- [ ] ` or `- [x] `. | `ChecklistLine.Item` | bullet point, task, todo |
| **checklist** | A note whose body has checklist items. | `Checklist` | list note |
| **recording** | A voice clip in a note, played from the note and its card. Synced as an attachment of the note's memo. | `Recording` | audio note, voice memo (memo is the server's copy), attachment (that is the server's copy) |
| **photo** | An image in a note, taken with the camera or chosen from the phone, shown at the top of the note and its card. Synced as an attachment of the note's memo. | `Photo` | picture, image note, attachment (that is the server's copy) |
| **attachment** | A file linked to a memo on the memos server; a recording's or photo's copy there. The device keeps both in one table named for it. | `ServerAttachment`, `StoredAttachment` | resource (memos' old word) |
| **tag** | A `#word` in a note's title or body, which memos makes a tag of too. `#a/b` is the tag `a/b`, inside the tag `a`. | `NoteTags` | label (Keep's word), hashtag, category |
| **color** | The background a note is shown on, one of a fixed set. | `NoteColor` | theme, label |
| **pinned** | Kept at the top of the board. | `pinned` | starred, favorite |
| **position** | Where a note sits on the board; a higher one is nearer the top. Synced as the memo's create time. | `position` | rank, order index |
| **move** | Dragging a note to another place on the board. | `move` | reorder, sort |
| **selection** | The notes picked by a long press, which the selection bar acts on together. | `Selection` | multi-select, checked (that is a checklist item) |
| **archive** | Notes put away: off the board but kept and synced. | `archived` | trash (deleting is separate) |
| **trash** | Deleted notes, kept for the days set in settings (a week by default) and read-only until restored or deleted for good. Their memos are archived meanwhile. | `trashedAt`, `Shelf.Trash`, `Trash` | bin, recycle bin, archive |
| **shelf** | Which notes the board shows: the notes in use, the archive or the trash. | `Shelf` | tab, filter, view |
| **delete forever** | Deleting a note for good, from the trash or when its days there are up. | `deleteForever` | purge (that is dropping the local row once the server agrees) |
| **quick capture** | Starting a note from outside the board: a home screen widget, a launcher shortcut, the Quick Settings tile, or text or photos shared from another app. It opens ready to type. | `Capture`, `CaptureIntents` | quick note, share sheet |
| **board** | The screen of all notes as cards. | `BoardContent`, `BoardScreen` | home, list, grid |
| **calendar** | The board's other layout: a month of days, each marked with a dot per note dated on it (its memo's create time), and the picked day's notes as cards below. | `BoardMode.Calendar`, `CalendarMonth`, `NoteCalendar` | timeline, heatmap, view (that is not a shelf either) |
| **memos server** | The user's self-hosted [memos](https://usememos.com) instance. | `MemosApi`, `ServerAddress` | cloud, backend |
| **account** | A user on a memos server; the notes sync with one account at a time. | `NoteStore.syncWith` | profile, login |
| **server check** | Trying every call sync makes against a memos server, on a test memo of its own, and naming the ones that fail. | `ServerCheck`, `ServerReport`, `Capability` | compatibility check, version check, health check |
| **memo** | A note's copy on the memos server. | `ServerMemo`, `MemoState` | |
| **store listing** | The name, descriptions, icon, screenshots and per-version changelogs F-Droid shows for the app, kept under `fastlane/`. | `StoreListingTest` | metadata (that is F-Droid's build recipe for the app) |
| **sync** | Making the device's notes and the server's memos agree. | `SyncEngine` | backup, upload |
| **access token** | The memos personal access token the app signs in with. | `token` | password, key |
| **sign-in provider** | A site the memos server lets people sign in through, such as Google. | `SignInProvider` (memos: identity provider) | SSO, IdP |
| **browser sign-in** | Signing in on a sign-in provider's page in the browser, after which the server makes the app an access token. | `BrowserSignIn` | login (except the `login` command), OAuth |
| **conflict copy** | A new note holding the server's version when both sides changed a note. | `keepServerCopy` | duplicate |
