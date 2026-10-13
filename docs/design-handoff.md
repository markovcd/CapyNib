# Design handoff: icon, color scheme and UI style

What a designer needs to know to restyle Capynib: what the app is, every screen and control, what
can change freely and what cannot, and where each design token lives in the code so the result
drops in without guesswork. The current identity (icon, teal-grey scheme, note palette, Figtree)
comes from the [Capynib identity canvas](https://claude.ai/artifact/RQR8sA3JUE7e8qHsUtp94r).

## The app in one paragraph

Capynib is a quick-notes app for Android in the spirit of Google Keep. The board shows notes as
colored cards in a staggered grid; tapping one opens it full screen in its color. A note has a
title and a body; the body can be plain text or a checklist whose items tick off. Notes can be
pinned to the top, archived, searched and deleted; deleted notes wait in a trash for a
week or the days set in settings. Everything is saved on the phone as you type and
synced to the user's own [memos](https://usememos.com) server when one is set up. The first version
has no text formatting on purpose: just text and checklists.

Personality to aim for: quick, light, tactile, calm. The cards are the content; chrome stays
quiet.

## What is wanted

1. **Launcher icon and logo.** An adaptive icon (Android 8+): a foreground layer, a background
   layer, and a monochrome layer for themed icons. Each is 108 x 108 dp with the meaningful part
   inside the central 72 dp circle (anything outside may be cropped by the launcher mask). Vector
   (SVG or Android vector drawable) preferred, flat shapes, no fine detail under 2 dp. A wordmark
   or logo lockup for the README and store listing is welcome too.
2. **App color scheme.** A Material 3 scheme for light and dark: at least `primary`, `onPrimary`,
   `primaryContainer`, `onPrimaryContainer`, `secondaryContainer`, `onSecondaryContainer`,
   `background`, `surface`, `surfaceContainerHigh`, `outlineVariant`. A seed color plus the
   generated scheme (Material Theme Builder export) is ideal. The app does not use Android 12
   dynamic (wallpaper) color; say if you want it to.
3. **Note palette.** The fill for each note color, in light and dark themes. See the constraints
   below; this is the most visible part of the app.
4. **UI style.** Card corner radius, outline or shadow for cards, spacing, the typeface (a Google
   Font is easy to bundle) and type scale, the search bar shape, the floating buttons, and the
   checkbox and color-swatch look in the editor. Mockups of the three screens are the clearest
   way to say it.

## Screens and controls

**Board** (`ui/board/BoardScreen.kt`, `NoteCard.kt`)
- Top bar: a rounded search field ("Search your notes"), a sync status icon, an archive toggle
  (archive box; a light bulb when showing the archive), a trash toggle (trash can; a light bulb
  when showing the trash), and a settings gear. The trash opens with one line saying how many days
  notes stay there.
- Tag bar, under the top bar when any note has a tag: a row of filter chips ("#travel"), one per
  tag on the shelf, scrolling sideways. Tapping one shows only its notes; tapping it again shows all.
- Body: a staggered grid of note cards, about 160 dp wide columns. Sections "Pinned" and
  "Others" get small labels when there are pinned notes. An empty board shows one centered line
  ("Notes you add appear here").
- A card: the note color as fill (default notes have a thin outline instead), the title in a
  title style, then up to 10 lines of the body; checklist items show a small checkbox glyph,
  ticked ones struck through. The note's tags follow as small tinted pills ("#travel"). A picked card wears a 2 dp `onSurface` outline.
- Gestures: a long press picks a card and the top bar becomes the selection bar (close, "N
  selected", pin, color, archive, delete, on `secondaryContainer`); moving the finger after the long
  press drags the card to a new place instead, lifted with a shadow. A swipe left archives a
  card, a swipe right pins or unpins it. Archive, unarchive and delete from the board show a snackbar with Undo.
- Floating buttons: an extended "New note" button with a pencil, and a small "New list" button
  with a checkbox above it.

**Note** (`ui/editor/EditorScreen.kt`)
- The whole screen takes the note's color.
- Top bar, transparent: back, pin (outlined or filled pin), archive or unarchive, delete. A note
  in the trash is read-only, with no bottom bar, and its top bar has restore and delete forever, which asks
  first in a dialog.
- A borderless title field ("Title") and either a borderless body field ("Note") or a checklist:
  rows of checkbox, item text and a remove (x) button; an "+ List item" row; then "N checked
  items" and the ticked rows, struck through.
- Bottom bar: a "show/hide checkboxes" button and a row of 12 round color swatches (the selected
  one has a ring and a check mark).

**Settings** (`ui/settings/SettingsScreen.kt`)
- Not connected: server address field, a full-width Sign in with browser button with a help line
  and the redirect address to allow (selectable, monospace), the access token field (outlined) and a Connect button. Connected: only a Disconnect button.
- Both: a message line ("Connected to ... as ...") and the sync status with its icon, then a
  Trash section with a number field for the days deleted notes stay in the trash (1 to 365).

**Sync status icons** (`ui/SyncStatusLabel.kt`): phone (notes only on this phone), plain cloud
(waiting), sync arrows (syncing), cloud with check (synced), crossed cloud (offline), error
circle (token refused or server error). Icons come from Material Icons (outlined); a different
icon set is fine if it covers the same meanings.

## Constraints

- **Note colors are fixed in number and name.** There are exactly 12: `default`, `coral`,
  `peach`, `sand`, `mint`, `sage`, `fog`, `storm`, `dusk`, `blossom`, `clay`, `chalk`. The names
  are saved in every note and on the memos server (as `#color/sand`), so they cannot be renamed,
  removed or reordered in meaning; only how each one looks can change. "default" means "no
  color" and follows the theme's surface.
- **Readable on every color.** Card text uses the theme's normal on-surface color: dark text on
  light-theme note colors, light text on dark-theme note colors. Each note color needs at least
  4.5:1 contrast with that text, in its theme. Pastels in light and deep tones in dark work; mid
  tones do not.
- **Distinguishable.** The 11 colors should stay tellable apart at swatch size (36 dp circles),
  including for common color-vision deficiencies where possible, and the light and dark versions
  of one color should feel like the same color.
- **Material 3 in Jetpack Compose.** Anything expressible as Material 3 color roles, typography
  and shapes is cheap; fully custom components are possible but cost more. Edge-to-edge layout:
  the board and note draw behind the status and navigation bars.
- **Android 8 (API 26) and up**, phones first; the grid adapts to tablets by adding columns.
- No text formatting controls in this version; please don't design a formatting toolbar.

## Where the tokens live

| Token | File |
|---|---|
| App color scheme (light and dark) | `app/src/main/kotlin/io/github/markovcd/capynib/ui/theme/Theme.kt` (`LightColors`, `DarkColors`) |
| Note palette (light and dark) | `app/src/main/kotlin/io/github/markovcd/capynib/ui/theme/NoteColors.kt` (`light`, `dark` maps); the only place note colors are defined |
| Typography | `ui/theme/CapynibType.kt` (Figtree in `res/font/`, license in `licenses/`) |
| Sync status icon colors | `ui/theme/SyncToneColor.kt` |
| Card corner radius, outline | `ui/board/NoteCard.kt` (16 dp, 1 dp `outlineVariant` outline on default notes) |
| Window color before the UI draws | `app/src/main/res/values/colors.xml`, `values-night/colors.xml` (`window_background`) |
| Launcher icon | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `res/values/colors.xml` (`ic_launcher_background`) |
| UI text | `app/src/main/res/values/strings.xml` |

## How to deliver

Any of these works, in order of preference:

1. Hex values for the tokens above (a Material Theme Builder export for the scheme, a table for
   the note palette), the icon layers as SVG or vector drawables, and mockups of the three screens.
2. A change to the files above directly. The gate (`./gate.sh`) builds and tests it; the UI tests
   find controls by tag, not by look, so restyling does not break them.

The current look can be tried from the debug APK each CI run on `main` uploads (artifact
`capynib-debug-apk` on the `gate` workflow).
