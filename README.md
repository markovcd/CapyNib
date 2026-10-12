# QuickNib

Quick notes for Android in the style of Google Keep: colored cards, checklists you can tick, pinned
notes and an archive. Notes live on the phone and work offline; when a [memos](https://usememos.com)
server is set up, every change syncs to it about a second after you type, and edits made on the
server (or another device) come back.

## Use it

1. Install the debug APK from the latest [gate run](../../actions/workflows/gate.yml) (artifact `quicknib-debug-apk`).
2. Write notes. Without a server they stay on the phone.
3. To sync, open **Settings** (gear on the board), enter your memos address and a personal access
   token (memos web UI: Settings, then Access Tokens), and tap **Connect**.

Each note is one private memo. The title is the memo's first-line heading and the color is a
`#color/<name>` tag at the end, so notes read naturally in the memos web UI too. See
[docs/adr/0002-a-note-is-one-memo.md](docs/adr/0002-a-note-is-one-memo.md).

## Build and test

| What | Command |
|---|---|
| Everything, as CI runs it (needs Docker) | `./gate.sh` |
| Unit and UI tests, no server | `./gradlew check` |
| Include the live memos tests | `docker run -d -p 5230:5230 neosmemo/memos:stable` then `MEMOS_TEST_URL=http://localhost:5230 ./gradlew check` |
| Debug APK | `./gradlew :app:assembleDebug` |
| The command line | `./gradlew :cli:installDist` then `cli/build/install/quicknib/bin/quicknib --help` |

The command line works on the same notes and sync as the app (`list`, `add`, `edit`, `check`,
`sync`, with `--json`), against a local database file.

## Design

The icon, color scheme and UI style are open for a design pass: see [docs/design-handoff.md](docs/design-handoff.md).

## License

[MIT](LICENSE)
