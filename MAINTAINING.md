# Maintaining CozyGrams

How to keep this repository current and its supply chain locked down.
Nothing here needs special tools beyond `gh` and the Android SDK.

## The weekly routine

Dependabot opens update PRs every week, and only once a release is at least
3 days old (a hijacked release has time to be pulled first):

- **One PR for every GitHub Action bump.** Actions are pinned to commit
  SHAs, and Dependabot moves the SHA and the `# vX.Y.Z` comment together.
- **One PR for minor and patch library bumps** (AndroidX in its own group
  where there is one).
- **One PR per major version**, so its release notes get a read first.

Merge a PR (squash) once its checks are green. The `Main` ruleset requires
`build`, `Analyze (java-kotlin)`, `Analyze (actions)` to pass, so a red PR can't be merged by accident. Dependabot
security updates skip the cooldown and can arrive any day; treat those first.

## Toolchain bumps by hand

Dependabot covers the Gradle plugins, the libraries and the Gradle wrapper.
For anything else:

- **Gradle wrapper** (if Dependabot hasn't): take the checksum from
  <https://gradle.org/release-checksums/> and run
  `./gradlew wrapper --gradle-version <v> --gradle-distribution-sha256-sum <sha>`.
  Keep `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`.
- **New Android API level**: change `compileSdk` and `targetSdk` in
  `app/build.gradle`, and the `platforms;android-NN.0 build-tools;NN.0.0`
  packages in `release.yml`, `codeql.yml` under `.github/workflows/`.
- **JDK**: keep the `java-version` in every workflow in step with the
  Java/Kotlin target in `app/build.gradle`.
- **A new GitHub Action**: pin it to a full commit SHA with a version comment.
  The repository only runs GitHub's own actions plus the ones on its allow
  list (see below); add the new one there first.

## Changing what the screen says

New or longer words are checked for you: `./gradlew test` includes `textFitAudit`, which
draws every screen at every supported size, text size and input mode and fails if any
text leaves its container (see "The text-fit guard" in the README). When it fails, the
report names the screen state and the variant; `tools/preview/audit.sh --show` paints it.
Fix the layout or tighten the copy; never shrink words below the prose floor to make them fit.

## Releasing

Every push to `main` builds a signed APK and Play bundle in
`.github/workflows/release.yml`. The job attests their build provenance and
replaces the single GitHub release. The `versionCode` is the run number.
For Google Play, upload the `.aab` to **Closed testing - Alpha**, attach
`mapping.txt`, and paste `fastlane/metadata/android/en-US/changelogs/default.txt`
as the release notes.

## Security settings that live outside the repository

These are GitHub settings, not files, so they are listed here to check or
recreate them:

| Setting | Value |
|---|---|
| Ruleset `Main` (default branch) | No deletion or force push, linear history, changes through a squash-merged PR, required checks: `build`, `Analyze (java-kotlin)`, `Analyze (actions)` |
| Merge buttons | Squash only, PR title as the commit title, branches deleted after merge |
| Actions permissions | GitHub-owned actions plus `android-actions/setup-android`, `gradle/actions/*` and `reactivecircus/android-emulator-runner`; full-SHA pinning required |
| Default `GITHUB_TOKEN` | Read-only; jobs ask for more themselves |
| Fork PR workflows | Approval required for every outside contributor |
| Dependabot | Alerts and security updates on; version updates from `.github/dependabot.yml` |
| Code scanning | CodeQL from `.github/workflows/codeql.yml` (not default setup: Kotlin needs a traced build) |
| Secret scanning | On, with push protection |
| Private vulnerability reporting | On (see `SECURITY.md`) |

Possible next step: move the four signing secrets from repository secrets
into a `release` environment that only `main` can deploy to, and add
`environment: release` to the build job. The workflow already keeps them
away from PRs and other branches; the environment would enforce that in
GitHub itself. Secrets can't be read back, so this means pasting them in
again.
