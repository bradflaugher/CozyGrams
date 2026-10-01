# Security

CozyGrams is distributed as a signed APK from this repository's GitHub
Releases and on Google Play. The Play build is the `CozyGrams.aab` from the
same CI run.

## Reporting a vulnerability

Please report vulnerabilities privately via
[GitHub security advisories](https://github.com/bradflaugher/CozyGrams/security/advisories/new)
rather than opening a public issue.

## Verifying a release

Each release APK is built by GitHub Actions from the `main` branch and
published alongside a `CozyGrams.apk.sha256` checksum. Verify a download
with:

```sh
sha256sum -c CozyGrams.apk.sha256
```

Each APK and bundle also carries a signed build provenance attestation,
which proves it was built by this repository's CI from a specific commit:

```sh
gh attestation verify CozyGrams.apk --repo bradflaugher/CozyGrams
```

## Design notes

- The app requests no permissions at all. There is no `INTERNET`
  permission, so nothing is sent off the device; there are no ads,
  analytics or accounts.
- Backups carry exactly one file: the save (`save.xml`: Story Book progress,
  the board in hand and the comfort settings) goes to the player's own
  Google backup and across a device-to-device transfer, and nothing else
  does (`data_extraction_rules.xml`, and `backup_rules.xml` for Android 8 to
  11).
- The only exported component is the launcher activity.
- The app has no third-party libraries. CI actions are pinned to commit
  SHAs, the Gradle distribution is checksum pinned, signing secrets only
  reach builds of `main`, Dependabot keeps the Gradle plugin and action pins
  current, and CodeQL scans the Kotlin sources and the workflows.
