# 2. A note is one memo, with its title and color written into the Markdown

## Context

Memos (v0.31) stores a memo as Markdown `content` plus `pinned` and `state` (normal or archived).
It has no fields for a title or a color, and Capynib's first version has no text formatting
beyond checklist items.

## Decision

A note is one private memo whose id is the note's id. The content is:

```
# Title

body text, with - [ ] and - [x] checklist items

#color/sand
```

- The title is the first line as an H1, which memos itself shows as the memo's title.
- The color is a trailing `#color/<key>` tag, which memos lists as a tag and filters by.
- Checklist items are memos' own task-list syntax, so ticking works in the memos web UI too.
- Pinned is `pinned`; archived is the `ARCHIVED` state.
- A body line that would be misread as the title or the color tag, or that starts with a backslash,
  is written with a leading backslash, so every note reads back exactly (`MemoCodecTest`).

Color keys (`coral`, `sand`, ...) are persisted and never renamed.

## Consequences

- Notes are readable and editable in the memos web UI; memos written there show up as notes.
- Any other Markdown in a memo is kept as plain text in the body, not rendered.
