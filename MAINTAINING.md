# Maintaining CozyGrams

How to keep this repository current and its supply chain locked down.
Nothing here needs special tools beyond `gh` and the Android SDK.

## The quarterly routine

Dependabot follows one policy: patch vulnerabilities fast, refresh everything
else rarely.

- **Security updates** open a PR as soon as an advisory affects the app, any
  day of the year. Treat those first.
- **Version updates** arrive as one combined PR per quarter: Gradle plugins
  and libraries, the Gradle wrapper, fastlane and the Actions pins
  together. Only releases at least 30 days old are proposed, so a broken or
  hijacked release has usually been pulled first. Majors come in the same PR;
  read their release notes before merging. Actions are pinned to commit SHAs,
  and Dependabot moves the SHA and the `# vX.Y.Z` comment together.

Merge a PR (squash) once its checks are green. The `Main` ruleset requires
`build`, `Analyze (java-kotlin)`, `Analyze (actions)` to pass, so a red PR can't be merged by accident.

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

## The website

[bradflaugher.github.io/CozyGrams](https://bradflaugher.github.io/CozyGrams/) is plain
HTML and CSS in `site/`, with no build step: the landing page, the privacy policy
(`site/privacy/index.html`, the address Play Console and the Cozy Corner point to) and a
404. Edit it in a pull request like anything else; `.github/workflows/pages.yml` checks it
on the PR (`python3 .github/scripts/check_site.py` runs the same check locally) and
deploys it to GitHub Pages when it reaches `main`. Its pictures are copies of
the store screenshots and the game's own art, so re-copy them (as WebP, about 1280 wide)
when those change. It loads nothing from anywhere else, and should stay that way.

If the policy changes, change `site/privacy/` and its "Last updated" date in the same PR as
the code that made it true. The privacy row in the Cozy Corner shows the policy's address
as text (`PRIVACY_URL` in `SettingsScene.java`), because a television may have no browser.

## Releasing

Everything ships from GitHub: merging to `main` is the release.

### Shipping an update

1. Make the change on a branch and open a pull request to `main`.
2. In the same pull request, update the release notes in
   `fastlane/metadata/android/en-US/changelogs/default.txt` (at most 500
   characters). Testers, and later Play users, see this text. A
   `changelogs/<versionCode>.txt` overrides it for one build.
3. CI runs the build, tests, lint, CodeQL and the `store listing` check, and
   Codex reviews the pull request. Squash-merge once it is green.
4. The push to `main` builds and signs the APK and Play bundle, replaces the
   GitHub release (the sideload APK), uploads the bundle to Google Play's
   **Closed testing - Alpha** track with the release notes, and syncs the
   store listing. The version code is the workflow run number, so it is never
   bumped by hand.
5. Play reviews the update by itself (managed publishing is off); follow it in
   Play Console › the app › **Publishing overview**.

### Changing the store listing

The listing lives in `fastlane/metadata/android/en-US/`: `title.txt` (at
most 30 characters), `short_description.txt` (80), `full_description.txt`
(4000) and `images/` (icon, feature graphic, screenshots). For the images,
re-render them with `tools/preview/render.sh` (store frames land in `tools/preview/out/store/`; captions live in `STORE_CAPTIONS` in `Preview.java`; see the README) and copy them into `images/`, then commit them. Store screenshots are promo shots: a headline and
subline outside the framed capture, never bare captures. Only images that
changed are uploaded (Play's copies are compared by checksum), and each change
goes through Play's review.

### Going to production

Production needs Play's production access first: for a personal developer
account, 12 testers opted in to the closed test for 14 days, then apply in
Play Console. After that, run **Actions › Promote to production**
(`play-promote.yml`):

- action `promote`, rollout `1`: the completed release on Closed testing -
  Alpha goes to everyone in production, as is, release notes included. A
  rollout below 1 (say `0.2`) starts a staged rollout instead.
- action `update rollout`: widens a staged rollout (a larger fraction) or
  finishes it (`1`).

Both check the release's status first: `promote` only takes a completed alpha
release and `update rollout` only an in-progress production rollout, so a
draft is never published. Before production access is granted, Play refuses
and the run fails.

### When something fails

- **`Google Play` job fails:** nothing reached Play. Its log says why (often a
  listing rule); fix it, or rerun the job from the Actions page.
- **`store listing` fails on a pull request:** the error names the file and
  the Play rule it breaks. Run the check locally with
  `python3 .github/scripts/check_store_listing.py` (needs `python3-pil` or
  `pip install pillow`).
- **Never rename `.github/workflows/release.yml`:** the run number, and with
  it the version code, would restart, and Play rejects any version code that
  isn't higher than the last.

### How it works

The `Google Play` job in `.github/workflows/release.yml` runs fastlane `supply`
(pinned in `Gemfile.lock`, on the latest stable Ruby) after `build`. It
uploads the bundle and `mapping.txt`, then syncs the listing text, icon,
feature graphic and screenshots from `fastlane/metadata/android`. It signs in
as the Google Cloud service account
`github-play-publisher@play-publishing-511400.iam.gserviceaccount.com`, whose
JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret of the `google-play`
environment (which only `main` can deploy to). `play-promote.yml` uses the
same environment, and `.github/scripts/play_release.rb` does its status
checks. The `release` job attests the APK's and bundle's build provenance and
replaces the single GitHub release.

## Security settings that live outside the repository

These are GitHub settings, not files, so they are listed here to check or
recreate them:

| Setting | Value |
|---|---|
| Ruleset `Main` (default branch) | No deletion or force push, linear history, changes through a squash-merged PR, required checks: `build`, `Analyze (java-kotlin)`, `Analyze (actions)`, `store listing` |
| Merge buttons | Squash only, PR title as the commit title, branches deleted after merge |
| Actions permissions | GitHub-owned actions plus `android-actions/setup-android`, `gradle/actions/*`, `reactivecircus/android-emulator-runner` and `ruby/setup-ruby`; full-SHA pinning required |
| Google Play API access | Service account `github-play-publisher@play-publishing-511400.iam.gserviceaccount.com` (Google Cloud project `play-publishing-511400`), invited in Play Console › Users and permissions with account-wide: view app information, release to production, release to testing tracks, manage store presence. Its JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret of the `google-play` environment |
| GitHub Pages | Source: GitHub Actions (`.github/workflows/pages.yml`), HTTPS enforced; the `github-pages` environment deploys from `main` only |
| Environment `google-play` | Deployable from `main` only; holds `PLAY_SERVICE_ACCOUNT_JSON`; the `Google Play` job and `play-promote.yml` run in it |
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
