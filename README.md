# Capynib

Quick notes for Android in the style of Google Keep: colored cards, checklists you can tick, pinned
notes and an archive. Notes live on the phone and work offline; when a [memos](https://usememos.com)
server is set up, every change syncs to it about a second after you type, and edits made on the
server (or another device) come back.

## Use it

1. Install the debug APK from the latest [gate run](../../actions/workflows/gate.yml) (artifact `capynib-debug-apk`).
2. Write notes. Without a server they stay on the phone.
3. To sync, open **Settings** (gear on the board), enter your memos address, and either tap
   **Sign in with browser** or paste a personal access token (memos web UI: Settings, then Access
   Tokens) and tap **Connect**.

Signing in with the browser works when the memos server signs people in through another site, such
as Google. That site sends the browser back to `http://127.0.0.1:47815/quicknib` on the phone, so
the address has to be listed as an authorized redirect URI on the server's OAuth client (for Google:
Cloud Console, APIs & Services, Credentials). Capynib then asks memos for an access token of its
own. See [docs/adr/0005-browser-sign-in-comes-back-to-the-phone-over-loopback.md](docs/adr/0005-browser-sign-in-comes-back-to-the-phone-over-loopback.md).

Each note is one private memo. The title is the memo's first-line heading and the color is a
`#color/<name>` tag at the end, so notes read naturally in the memos web UI too. See
[docs/adr/0002-a-note-is-one-memo.md](docs/adr/0002-a-note-is-one-memo.md).

## Build and test

| What | Command |
|---|---|
| Everything, as CI runs it (needs Docker) | `./gate.sh` |
| Unit and UI tests, no server | `./gradlew check` |
| Include the live memos tests | `docker run -d -p 5230:5230 --add-host=host.docker.internal:host-gateway neosmemo/memos:stable` then `MEMOS_TEST_URL=http://localhost:5230 MEMOS_TEST_REACH_BACK=host.docker.internal ./gradlew check` |
| Debug APK | `./gradlew :app:assembleDebug` |
| Release APK, as F-Droid builds it | `./gradlew :app:assembleRelease` |
| The command line | `./gradlew :cli:installDist` then `cli/build/install/capynib/bin/capynib --help` |

The command line works on the same notes and sync as the app (`list`, `add`, `edit`, `check`,
`sync`, with `--json`), against a local database file. `capynib login --server URL` signs in with
the browser the same way and prints the new access token, for `CAPYNIB_TOKEN`.

## Design

The icon, colors and type come from the [Capynib identity canvas](https://claude.ai/artifact/RQR8sA3JUE7e8qHsUtp94r); [docs/design-handoff.md](docs/design-handoff.md) says where each token lives. The app uses [Figtree](https://github.com/erikdkennedy/figtree) under the [SIL Open Font License](licenses/Figtree-OFL.txt).

## License

[MIT](LICENSE)
