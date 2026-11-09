# TODO

- Lock dependency versions (`gradle --write-locks`) and verify the Gradle wrapper checksum (`distributionSha256Sum`); both need a machine that can reach Google's Maven repository and downloads.gradle.org.
- Turn on Android lint warnings as errors once the first CI runs show what lint reports.
- `release.sh` refuses and builds a release but does not tag it; give it the rest of `docs/agents/rules/pipeline.md` (tag, refuse when the gate has not passed on the commit).
- Switch the F-Droid merge request to the release key and get it merged: [docs/handoff/01-f-droid-submission.md](docs/handoff/01-f-droid-submission.md).
- Publish Capynib on Google Play: [docs/handoff/02-google-play.md](docs/handoff/02-google-play.md).
- Let the user pick a sign-in provider when a memos server offers more than one; browser sign-in uses the first (ADR 0005).
- `ChecklistEditorTest.A_new_list_takes_items_that_can_be_ticked` failed once in four full runs on 2026-10-14, and again on 2026-10-23: right after "+ List item", `item-1` was found only in the unmerged semantics tree. Cause not found.
