# R8 rules for the release build. Libraries ship their own; these cover what only this app knows.

# TagCharacters reads memos-tag-data.txt from its own package, so the package must keep its name.
-keepnames class io.github.markovcd.capynib.core.note.TagCharacters
