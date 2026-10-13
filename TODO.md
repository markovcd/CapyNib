# TODO

- Lock dependency versions (`gradle --write-locks`) and verify the Gradle wrapper checksum (`distributionSha256Sum`); both need a machine that can reach Google's Maven repository and downloads.gradle.org.
- Turn on Android lint warnings as errors once the first CI runs show what lint reports.
- Release script (`release.sh`) with signing, per `docs/agents/rules/pipeline.md`.
- Tag v0.1.0 and submit QuickNib to F-Droid: [docs/handoff/01-f-droid-submission.md](docs/handoff/01-f-droid-submission.md).
