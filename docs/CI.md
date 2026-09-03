# Fork CI

## Purpose

The fork must be buildable and testable on GitHub without access to secrets owned by `trakt/showly` and without depending on the LOG workstation.

S0.5 deliberately keeps this adaptation inside `.github/` instead of changing production Gradle behavior. This minimizes the conflict surface when pulling future upstream changes.

## Workflows

### `.github/workflows/fork-ci.yml`

Runs on branch pushes, pull requests and manual dispatches.

It performs:

1. Kotlin formatting verification with the same ktlint 1.5.0 baseline used upstream.
2. JDK 21 and Gradle setup on `ubuntu-latest` using `gradle/actions/setup-gradle@v6`.
3. Creation of runner-local `local.properties`. Trusted fork builds use the fork-owned Trakt OAuth credentials from `TRAKT_CLIENT_ID` and `TRAKT_CLIENT_SECRET` repository secrets; contexts without those secrets fall back to non-functional placeholders. TMDB and OMDb remain placeholders until separately configured.
4. Creation of runner-local placeholder keystore metadata and an empty keystore path so Gradle can configure the release signing block while only debug tasks are executed.
5. The existing app/repository/UI unit-test suites used by upstream.
6. `:app:assembleDebug`.
7. Upload of the debug APK for 14 days using `actions/upload-artifact@v7`.

The workflow never commits or prints Trakt credentials. It writes them only to the ephemeral runner when both repository secrets are available. Pull requests or other contexts without secrets continue to use non-functional placeholders so fork-safe CI remains reproducible.


## Fork-owned Trakt OAuth identity

The fork keeps Trakt as a first-class backend and uses its own Trakt application identity instead of upstream credentials.

Configure these GitHub Actions repository secrets:

- `TRAKT_CLIENT_ID`
- `TRAKT_CLIENT_SECRET`

The current upstream-compatible OAuth redirect URI is `showly2://trakt`; the Trakt application must list that redirect URI unless the application code is changed in a later stage.

On trusted branch pushes and manual runs where both secrets are available, the uploaded debug APK is built with the fork-owned Trakt OAuth identity. On pull requests or other secret-less contexts, the APK still builds but Trakt login is intentionally non-functional.

The values must never be committed to `local.properties`, documentation, workflow YAML, logs, or artifacts other than the compiled Android application where the Trakt client credentials are required at runtime.
### Upstream workflows

The inherited `.github/workflows/android.yml` and `.github/workflows/release.yml` depend on upstream-only encrypted files and `KEYSTORE_PASSPHRASE`.

Each inherited job is guarded with:

```yaml
if: github.repository == 'trakt/showly'
```

Therefore those workflows retain their upstream meaning but are skipped inside `nonlog/showly`. Fork release signing remains disabled until a separate fork release workflow and protected secrets are explicitly designed.

## Verified baseline

Verified on 2026-08-30 against `feat/ryot-foundation` commit `baf1a8a27ab989851e0bce6fbb7b2df9722c4bfe`.

GitHub Actions run: `33320407113`

Results:

- ktlint: passed;
- all selected upstream unit-test tasks: passed;
- debug APK build: passed;
- artifact upload: passed;
- artifact: `showly-debug-baf1a8a27ab989851e0bce6fbb7b2df9722c4bfe`;
- artifact digest: `sha256:21df3a28ab7f8b9de5b856f554f00e87432c47a001f71fa4e267b5666545c194`;
- artifact size: 15,287,526 bytes.

The verification run completed without the Node 20 deprecation annotations seen with the older action majors.

This is the S0.5 build/test baseline for subsequent Ryot work.
