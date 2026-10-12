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
| **color** | The background a note is shown on, one of a fixed set. | `NoteColor` | theme, label |
| **pinned** | Kept at the top of the board. | `pinned` | starred, favorite |
| **archive** | Notes put away: off the board but kept and synced. | `archived` | trash (deleting is separate) |
| **board** | The screen of all notes as cards. | `BoardContent`, `BoardScreen` | home, list, grid |
| **memos server** | The user's self-hosted [memos](https://usememos.com) instance. | `MemosApi`, `ServerAddress` | cloud, backend |
| **memo** | A note's copy on the memos server. | `ServerMemo`, `MemoState` | |
| **sync** | Making the device's notes and the server's memos agree. | `SyncEngine` | backup, upload |
| **access token** | The memos personal access token the app signs in with. | `token` | password, key |
| **conflict copy** | A new note holding the server's version when both sides changed a note. | `keepServerCopy` | duplicate |
