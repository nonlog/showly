# Fork CI

## Purpose

The fork must be buildable and testable on GitHub without access to secrets owned by `trakt/showly` and without depending on the LOG workstation.

S0.5 deliberately keeps this adaptation inside `.github/` instead of changing production Gradle behavior. This minimizes the conflict surface when pulling future upstream changes.

## Workflows

### `.github/workflows/fork-ci.yml`

Runs on branch pushes, pull requests and manual dispatches.

It performs:

1. Kotlin formatting verification with the same ktlint 1.5.0 baseline used upstream.
2. JDK 21 and Gradle setup on `ubuntu-latest`.
3. Creation of runner-local placeholder `local.properties` values for Trakt, TMDB and OMDb build constants.
4. Creation of runner-local placeholder keystore metadata and an empty keystore path so Gradle can configure the release signing block while only debug tasks are executed.
5. The existing app/repository/UI unit-test suites used by upstream.
6. `:app:assembleDebug`.
7. Upload of the debug APK for 14 days.

The placeholder values are intentionally non-secret and are created only inside the ephemeral GitHub Actions runner. They are never valid service credentials and are not committed to the repository.

### Upstream workflows

The inherited `.github/workflows/android.yml` and `.github/workflows/release.yml` depend on upstream-only encrypted files and `KEYSTORE_PASSPHRASE`.

Each inherited job is guarded with:

```yaml
if: github.repository == 'trakt/showly'
```

Therefore those workflows retain their upstream meaning but are skipped inside `nonlog/showly`. Fork release signing remains disabled until a separate fork release workflow and protected secrets are explicitly designed.

## Verified baseline

Verified on 2026-08-30 against `feat/ryot-foundation` commit `d10b00d26a930714b20d68c26981aa74ca102e3a`.

GitHub Actions run: `33319955346`

Results:

- ktlint: passed;
- all selected upstream unit-test tasks: passed;
- debug APK build: passed;
- artifact upload: passed;
- artifact: `showly-debug-d10b00d26a930714b20d68c26981aa74ca102e3a`;
- artifact digest: `sha256:4e9607fee3ff7014a8f26dd3dbfd45dafbd4d4c5c08ad820496923efc75c64c6`;
- artifact size: 15,287,515 bytes.

This is the S0.5 build/test baseline for subsequent Ryot work.
