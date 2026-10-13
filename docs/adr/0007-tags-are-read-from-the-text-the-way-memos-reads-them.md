# 7. Tags are read from the note's text the way memos reads them

## Context

memos turns each `#word` in a memo into a tag, and `#a/b` into the tag `a/b` inside `a`. Capynib
shows tags on cards and narrows the board by one, offline as well, and a note's tags should be the
ones its memo has on the server. memos v0.31 finds tags with its own lexer (`markdown/parser/tag.go`)
over the literal text of a full GFM parse, leaving out code, links, autolinks and written URLs.

## Decision

- A tag is plain text in the title or body; nothing about tags is stored. They are read again
  from the text wherever they are needed (`NoteTags`), from the memo content the note encodes to.
- The lexer is a port of memos' (`TagLexer`). Its Unicode and emoji tables and the HTML entity
  names are copied from memos and goldmark into `memos-tag-data.txt` by
  `scripts/memos-tag-data.py`, rather than taken from the JDK or Android, whose Unicode versions
  differ from memos' and from each other. A newer memos means running the script again.
- Instead of a Markdown library, `MemoText` cuts out the parts of Markdown a plain-text note
  meets: fenced, indented and inline code, links and images with their reference definitions,
  angle autolinks, HTML tags and written URLs. Math, underscore emphasis around a tag, bare email
  addresses and a URL straight after a byte order mark are read as plain text, so their tags can
  differ from memos'. `NoteTagsTest` runs memos' own tag cases and names each of these.
- The color tag `#color/<key>` is not shown as a tag; it is the note's color.

## Consequences

- Tags work offline and need no migration; editing a tag is editing text.
- A note whose text uses math, `_#tag_` or an email address before a tag can show a tag memos does
  not list, or miss one it does. memos' own lists still come from the server's parse.
- `MemosServerTest` checks a set of everyday notes against a real memos server's tags, on 0.31 and
  newer. memos 0.28 to 0.30 end a tag at an apostrophe (`#O'Brien's` is `O`), so there a note
  such as that one shows a longer tag than memos lists.
