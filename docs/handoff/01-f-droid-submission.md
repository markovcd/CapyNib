# 01. Capynib is not on F-Droid yet

- **Severity:** Low
- **Status:** Open
- **Found:** 2026-10-13, on `main`

## What is wrong

The repository is ready for F-Droid (store listing under `fastlane/`, version in
`app/build.gradle.kts`, no non-free dependencies, release APK built by the gate; see
[ADR 0004](../adr/0004-the-version-is-written-in-the-build-file-and-a-tag-marks-it.md)), but
there is no release tag and no merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata).

## Suggested fix

1. Tag the release: `git tag -a v0.1.0 -m "Capynib 0.1.0"` on a commit whose gate run is green,
   then `git push origin v0.1.0`.
2. Fork fdroiddata on GitLab, add `metadata/io.github.markovcd.capynib.yml` with the recipe
   below, and check it with `fdroid readmeta`, `fdroid rewritemeta io.github.markovcd.capynib`,
   `fdroid lint io.github.markovcd.capynib` and `fdroid build -v -l io.github.markovcd.capynib`.
3. Open the merge request on branch `io.github.markovcd.capynib` using fdroiddata's "App
   inclusion" template.

Later releases need nothing on F-Droid's side: raise the version, add the changelog, tag.

```yaml
Categories:
  - Writing
License: MIT
AuthorName: markovcd
SourceCode: https://github.com/markovcd/CapyNib
IssueTracker: https://github.com/markovcd/CapyNib/issues
Changelog: https://github.com/markovcd/CapyNib/blob/HEAD/CHANGELOG.md

AutoName: Capynib

RepoType: git
Repo: https://github.com/markovcd/CapyNib.git

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v\d+\.\d+\.\d+$
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

`commit:` should be the tag's commit hash once it exists; reviewers prefer the hash to the tag
name.
