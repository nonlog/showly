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
- [x] Defer ratings in S3 until S4 defines a safe bridge projection; S4 now owns rating synchronization.
- [x] Map movie/show custom lists as an additive Floppy mirror with owned list metadata and TMDB membership adds.
- [x] Verify the S3 custom-list slice in fork CI.
- [ ] Validate custom-list create/update/add behavior on-device against an existing/manual Floppy list.
- [x] Define custom-list bootstrap as create-owned-without-name-adoption, preserving pre-existing Floppy lists/memberships.
- [x] Defer custom-list member/list deletions to S4 because Floppy list_item_id is renumbered and cannot serve as immutable ownership.
- [x] Define rating bootstrap/conflict behavior in the S4 bridge ledger.

## S4 - Bidirectional Trakt ↔ Floppy bridge

- [x] Adopt Showly as the bridge rather than treating Trakt as the permanent authoritative source.
- [x] Define latest-mutation-wins semantics with provider timestamps, observed tombstones, first-observation protection, and deterministic ties.
- [x] Add a durable Room bridge ledger and clear it when the Trakt/Floppy remote identity changes.
- [x] Implement bidirectional movie/show watchlist reconciliation by TMDB identity.
- [x] Implement bidirectional movie/episode history reconciliation with independent rewatch events and deletion tombstones.
- [x] Implement bidirectional movie/show rating reconciliation using Trakt's 1-10 title-rating projection without changing Floppy watch status.
- [x] Verify the core S4 bridge kernel (watchlist/history/ratings + ledger + credentials UI) in Fork CI #36.
- [x] Verify the Custom Lists latest-wins/tombstone migration in Fork CI #37.
- [x] Migrate custom-list identity, presence, metadata, and movie/show membership to the same latest-wins/tombstone model.
- [x] Add durable retry/queue behavior beyond retry-on-next-sync using the schema-43 domain queue + WorkManager exponential retry (verified in Fork CI #42 and on-device migration).
- [x] Add an explicit manual `Trakt ↔ Floppy` sync action and persist last-attempt/last-success/change-count/failed-domain status for the settings UI.
- [x] Visually validate the redesigned credentials UI on-device (confirmed by user on 2026-09-05).
- [x] Serialize manual/periodic full bridge runs and reset visible bridge status when the Floppy remote identity changes (verified in Fork CI #40).
- [ ] Promote Showly local History/Watchlist mutations to first-class three-way mutations instead of relying on Trakt as an intermediate. Schema-44 durable per-provider ACKs, direct Showly -> Floppy fast path, shared execution gate, pre-snapshot outbox drain, parent episode identity persistence, and legacy local-History recovery are verified through Fork CI #53 and on-device. The previously missing local S1E1/S1E2 now exist in both remotes; a fresh local UI add/remove matrix remains before closing this item.
- [ ] Reduce full-sync latency. The #53 on-device run improved from ~65.3 s to ~40.1 s after removing duplicate History/Watchlist post-pass work and Floppy History N+1 detail requests. A second request-reduction pass (connection-validation reuse + successful-bridge Watchlist duplicate skip + redundant delay removal) is implemented locally and awaits CI/re-measurement.
- [ ] Add per-domain pending/conflict detail beyond the current last-run summary. Pending-domain visibility is complete; item-level conflict detail remains deferred until live validation.
- [ ] Perform on-device conflict tests in both directions, including delete-vs-edit, rewatch, rating removal, and remote identity changes. Custom Lists and Watchlist are complete in controlled live tests; the Watchlist Trakt-deletion regression is fixed and verified on-device with CI #44. Ratings/History stale-export hardening awaits Fork CI/live validation.

## S5 - Additional user data

Evaluate hidden/dropped state, notes/comments, playback progress, and other capabilities only where both providers map cleanly.

## S6 - Deeper decoupling (optional)

Only after dual-backend tracking is stable, evaluate catalog decoupling and a provider-neutral local media key as separate migration projects.
