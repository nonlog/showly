# Roadmap

## S0 - Fork initialization and architecture discovery

- [x] Create nonlog/showly as a real fork of trakt/showly.
- [x] Record the upstream baseline.
- [x] Audit Trakt remote, repository, sync, identity, and local-storage coupling.
- [x] Define provider-neutral external identity rules.

## S0.5 - Fork-safe GitHub CI

- [x] Add branch/PR verification and debug APK artifacts.
- [x] Guard inherited upstream-only release jobs.
- [x] Keep GitHub Actions as the canonical build environment.

## S0.75 - Fork-owned Trakt OAuth identity

- [x] Keep the upstream Trakt OAuth implementation and showly2://trakt redirect contract.
- [x] Inject TRAKT_CLIENT_ID and TRAKT_CLIENT_SECRET from GitHub Actions repository secrets for trusted builds.
- [x] Preserve non-functional placeholders for secret-less CI contexts.
- [x] Produce a successful GitHub Actions APK build using the fork-owned Trakt OAuth credentials.
- [ ] Validate end-to-end Trakt login on-device with the GitHub-built APK.

## S1 - Minimal Floppy connectivity

- [x] Validate the deployed Floppy v26.8.27 authentication contract.
- [x] Add persisted Floppy enable/base URL/API-key configuration.
- [x] Add normalized URL validation.
- [x] Add public reachability check through /api/v1/info/.
- [x] Add authenticated connection check through /api/v1/user/preferences/ using X-API-Key.
- [x] Add settings UI and clear connection states.
- [x] Add focused data-remote tests to fork CI.
- [x] Verify the GitHub Actions build.
- [ ] Test the settings screen against a real user API token.

## S2 - Trakt to Floppy watched/history vertical slice

- [x] Introduce an isolated Floppy history runner after normal Trakt sync.
- [x] Read actual Trakt movie and episode history events so rewatch timestamps are preserved.
- [x] Export movie history to Floppy using TMDB identity.
- [x] Export episode history using parent-show TMDB identity plus season/episode coordinates.
- [x] Add separate movie/episode Trakt history checkpoints for incremental sync.
- [x] Deduplicate retries against Floppy consumptions by exact watched instant.
- [x] Keep Floppy failures non-fatal to the existing local/Trakt sync.
- [ ] Verify the S2 GitHub Actions build and perform an on-device bootstrap test.

## S3 - Watchlist, ratings, and lists

- [x] Mirror the local Showly movie/show watchlist to Floppy Planning consumptions using TMDB identity.
- [x] Reuse an existing Floppy Planning consumption instead of creating duplicates.
- [x] Track ownership of Showly-created Planning consumptions and delete only those exact rows on watchlist removal.
- [x] Fail safe when ownership is missing or a Floppy row was edited: preserve remote user data rather than guessing.
- [x] Keep Floppy watchlist failures non-fatal to existing Trakt synchronization.
- [x] Verify the S3 watchlist slice in fork CI.
- [ ] Perform an on-device watchlist bootstrap/add/remove test.
- [ ] Mirror ratings after a safe title-level score contract is defined; current Floppy score mutates a consumption row.
- [x] Map movie/show custom lists as an additive Floppy mirror with owned list metadata and TMDB membership adds.
- [x] Verify the S3 custom-list slice in fork CI.
- [ ] Validate custom-list create/update/add behavior on-device against an existing/manual Floppy list.
- [x] Define custom-list bootstrap as create-owned-without-name-adoption, preserving pre-existing Floppy lists/memberships.
- [x] Defer custom-list member/list deletions to S4 because Floppy list_item_id is renumbered and cannot serve as immutable ownership.
- [ ] Define ratings bootstrap only after a safe rating contract exists.

## S4 - Bidirectional synchronization

- Define authoritative-source modes.
- Define timestamp, deletion/tombstone, and duplicate rewatch semantics.
- Add durable Floppy queue/retry behavior.
- Add manual/background sync UX.
- Only then evaluate Floppy-originated changes flowing back through Showly local state to Trakt.

## S5 - Additional user data

Evaluate hidden/dropped state, notes/comments, playback progress, and other capabilities only where both providers map cleanly.

## S6 - Deeper decoupling (optional)

Only after dual-backend tracking is stable, evaluate catalog decoupling and a provider-neutral local media key as separate migration projects.
