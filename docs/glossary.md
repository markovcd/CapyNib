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
| **trash** | Deleted notes, kept for a week and read-only until restored or deleted for good. Their memos are archived meanwhile. | `trashedAt`, `Shelf.Trash`, `Trash` | bin, recycle bin, archive |
| **shelf** | Which notes the board shows: the notes in use, the archive or the trash. | `Shelf` | tab, filter, view |
| **delete forever** | Deleting a note for good, from the trash or when its week there is up. | `deleteForever` | purge (that is dropping the local row once the server agrees) |
| **board** | The screen of all notes as cards. | `BoardContent`, `BoardScreen` | home, list, grid |
| **memos server** | The user's self-hosted [memos](https://usememos.com) instance. | `MemosApi`, `ServerAddress` | cloud, backend |
| **memo** | A note's copy on the memos server. | `ServerMemo`, `MemoState` | |
| **store listing** | The name, descriptions, icon, screenshots and per-version changelogs F-Droid shows for the app, kept under `fastlane/`. | `StoreListingTest` | metadata (that is F-Droid's build recipe for the app) |
| **sync** | Making the device's notes and the server's memos agree. | `SyncEngine` | backup, upload |
| **access token** | The memos personal access token the app signs in with. | `token` | password, key |
| **sign-in provider** | A site the memos server lets people sign in through, such as Google. | `SignInProvider` (memos: identity provider) | SSO, IdP |
| **browser sign-in** | Signing in on a sign-in provider's page in the browser, after which the server makes the app an access token. | `BrowserSignIn` | login (except the `login` command), OAuth |
| **conflict copy** | A new note holding the server's version when both sides changed a note. | `keepServerCopy` | duplicate |
