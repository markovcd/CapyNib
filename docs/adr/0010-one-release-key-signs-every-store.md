# 10. One release key signs Capynib in every store, and F-Droid publishes our APK when its own build matches it

## Context

Capynib ships through F-Droid and Google Play. Android installs an update only over an app signed
with the same key, so if each store signed with a key of its own, moving from one store to the other
would mean uninstalling, and uninstalling deletes the notes that have not synced. F-Droid signs with
its own key unless the app's recipe lists a published APK (`Binaries:`) and the key that signs it
(`AllowedAPKSigningKeys:`); it then builds the same tag, and publishes the published APK only if its
own unsigned build is the same file with the signature taken off. A recipe cannot move from F-Droid's
key to the project's once F-Droid has shipped a build signed with its own.

## Decision

- The project keeps one release key. The release workflow holds it as GitHub secrets; no copy is in
  the repository.
- A pushed tag `v<versionName>` runs the gate on the tagged commit, then `release.sh` builds the APK
  and the app bundle in the gate's pinned Android SDK image, signed with that key, and the workflow
  publishes both as the tag's GitHub release.
- The F-Droid recipe points `Binaries:` at that release's APK and allows only that key.
- Google Play gets the app bundle, with Play App Signing given the same key as the app signing key,
  so a Play install takes an F-Droid update and the other way round.
- The release build stays reproducible: the same commit builds the same unsigned APK on any machine
  and from any folder. A change that breaks that stops F-Droid publishing the release.

## Consequences

- Losing the key means no store can update an installed Capynib again. The owner keeps a backup
  outside GitHub.
- A release whose APK F-Droid cannot reproduce is not published on F-Droid; its build log says which
  files differ.
- A local `./release.sh` signs with a throwaway key, so trying a release needs no secret.
