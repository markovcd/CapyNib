#!/usr/bin/env python3
"""Writes the character tables Capynib's tag lexer shares with memos.

usage: memos-tag-data.py MEMOS_CHECKOUT GOLDMARK_CHECKOUT

MEMOS_CHECKOUT is a clone of github.com/usememos/memos at the server version Capynib targets, and
GOLDMARK_CHECKOUT one of github.com/yuin/goldmark at the version that memos' go.mod names.
"""
import json
import re
import sys
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "core/src/main/resources/io/github/markovcd/capynib/core/note/memos-tag-data.txt"


def ranges(go: str, name: str) -> list[str]:
    block = re.search(r"var " + name + r" = \[\.\.\.\]codePointRange\{(.*?)\n\}", go, re.S)
    if block is None:
        sys.exit(f"no table {name} in tag_unicode_tables.go")
    return [f"{lo[2:]}-{hi[2:]}" for lo, hi in re.findall(r"\{lo: (0x[0-9A-Fa-f]+), hi: (0x[0-9A-Fa-f]+)\}", block.group(1))]


def emoji(go: str) -> list[str]:
    block = re.search(r"var emoji17FullyQualified = map\[string\]struct\{\}\{(.*?)\n\}", go, re.S)
    if block is None:
        sys.exit("no emoji table in tag_unicode_tables.go")
    quoted = re.findall(r'^\s*"((?:[^"\\]|\\.)*)":\s*\{\},$', block.group(1), re.M)
    decoded = [q.encode("ascii").decode("unicode_escape") for q in quoted]
    return [" ".join(f"{ord(c):X}" for c in e) for e in decoded]


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    memos, goldmark = Path(sys.argv[1]), Path(sys.argv[2])
    go = (memos / "markdown/parser/tag_unicode_tables.go").read_text(encoding="utf-8")
    entities = json.loads((goldmark / "_tools/html5entities.json").read_text(encoding="utf-8"))["data"]

    sections = {
        "xid-continue": ranges(go, "unicode17XIDContinue"),
        "default-ignorable": ranges(go, "unicode17DefaultIgnorable"),
        "combining-marks": ranges(go, "unicode17CombiningMarks"),
        "emoji": emoji(go),
        "entities": sorted(e["Name"] for e in entities),
    }
    lines = [
        "# Written by scripts/memos-tag-data.py from memos' markdown/parser/tag_unicode_tables.go and",
        "# goldmark's _tools/html5entities.json. Code points are hex; an emoji is one per line.",
    ]
    for name, rows in sections.items():
        lines.append(f"[{name}]")
        lines.extend(rows)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{OUTPUT}: " + ", ".join(f"{len(rows)} {name}" for name, rows in sections.items()))


if __name__ == "__main__":
    main()
