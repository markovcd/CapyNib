# 01. Capynib is not on F-Droid yet

- **Severity:** Low
- **Status:** Open
- **Found:** 2026-10-13, on `main`; recipe changed 2026-11-09

## What is wrong

The merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/52390) is
open and not merged. Its recipe builds v0.1.1 and lets F-Droid sign with its own key; Capynib now
signs every store's build with one release key ([ADR 0010](../adr/0010-one-release-key-signs-every-store.md)),
so the recipe has to change before the merge request is merged.

## Suggested fix

1. Make the release key, alias `capynib` in a PKCS12 keystore, write its fingerprint in `release-key.txt`, and store it as the release
   workflow's secrets `RELEASE_KEYSTORE_BASE64` and `RELEASE_KEYSTORE_PASSWORD`.
2. Release the next version: raise `versionCode` and `versionName`, add its store changelog, tag
   `v<versionName>` and push the tag. The release workflow publishes the signed APK.
3. Change the merge request's recipe to the one below, with that release's commit hash and the
   key's SHA-256 certificate fingerprint (lowercase, no colons; `release.sh` prints it), and check
   the merge request's pipeline: its build job compares F-Droid's build with the published APK.

Later releases need nothing on F-Droid's side: raise the version, add the changelog, tag.

```yaml
Categories:
  - Note
  - Task
License: MIT
AuthorName: markovcd
SourceCode: https://github.com/markovcd/CapyNib
IssueTracker: https://github.com/markovcd/CapyNib/issues
Changelog: https://github.com/markovcd/CapyNib/blob/HEAD/CHANGELOG.md

AutoName: Capynib

RepoType: git
Repo: https://github.com/markovcd/CapyNib.git
Binaries: https://github.com/markovcd/CapyNib/releases/download/v%v/capynib-%v.apk

Builds:
  - versionName: 0.2.0
    versionCode: 3
    commit: <the v0.2.0 commit hash>
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: 7ed522a5aa8a4075c4378b41bd0b6fa7229e56cc5eb20bae7619cc4e2ffdb422

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v\d+\.\d+\.\d+$
CurrentVersion: 0.2.0
CurrentVersionCode: 3
```
