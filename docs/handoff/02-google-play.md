# 02. Capynib is not on Google Play yet

- **Severity:** Low
- **Status:** Open
- **Found:** 2026-11-09, on `main`

## What is wrong

Each tagged release publishes a signed app bundle (`capynib-<version>.aab`) on its GitHub release
([ADR 0010](../adr/0010-one-release-key-signs-every-store.md)), but there is no Play Console app.

## Suggested fix

1. A Google Play developer account, with its identity check done.
2. Create the app `io.github.markovcd.capynib`. At the first upload, under Play App Signing, choose
   to use your own key and upload the release key with Google's PEPK tool, so Play signs with the
   same key as F-Droid. The same key uploads the app bundle.
3. Store listing: the texts, icon and screenshots in `fastlane/metadata/android/en-US`, plus a
   1024×500 feature graphic.
4. A privacy policy URL, the data safety form (notes, photos and recordings go only to the user's
   own memos server), the content rating questionnaire and the target audience.
5. A personal developer account made after November 2023 has to run a closed test with at least 12
   testers for 14 days before it can publish to production.
6. Upload the release's app bundle from its GitHub release.
