# 4. The version is written in the app's build file, and a tag marks each release

## Context

Capynib ships through F-Droid, which builds the app from source at a git tag and finds new
releases by reading `versionCode` and `versionName` from the Gradle build file at each tag. It
cannot read a version computed at build time, from a Gradle property or from `git describe`.
The pipeline rules ask that a version come from a tag and never be edited into a file; for this
app that rule and F-Droid cannot both hold.

## Decision

- `versionCode` and `versionName` are literals in `app/build.gradle.kts`. A release is one commit
  that raises both and adds `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, then
  the tag `v<versionName>` on that commit.
- `versionCode` goes up by one per release.
- Debug builds carry the suffix `-dev`, so a CI artifact never passes for a release. Release builds
  carry the plain version.
- The release APK leaves out the dependency metadata block, which only Google can decrypt.
- F-Droid builds and signs the release APK with its own key; the project keeps no signing key. `StoreListingTest` holds the store
  listing to F-Droid's limits and checks the current version has a changelog.

## Consequences

- F-Droid picks up a new release on its own from the tag (`UpdateCheckMode: Tags`), with no merge
  request per release.
- The version in `main` between releases is the last one released; only the `-dev` suffix on debug
  builds tells them apart.
- Capynib has no release key of its own: F-Droid is the one place releases come from, signed with
  F-Droid's key.
