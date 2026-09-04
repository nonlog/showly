# Development Handoff

Last updated: 2026-09-04

## Active line

- Repository: `nonlog/showly`
- Active branch: `feat/runtime-credentials-free-features`
- Upstream baseline: `trakt/showly@ec897b65b1b55c18ce24a755f83f894f422e559a`
- Current verified local/GitHub head before S3 work: `9cd1935db2ec6b5de0d3f5fa921d063544dc37ca` (`style: fix list view ktlint`)
- Commit identity for agent-created commits: `Codex <codex@openai.com>` for both author and committer.
- GitHub Actions `Fork CI` is the canonical validation environment.

## Current CI state

- Run #28 (`33774902942`) on `339f120` built and uploaded the debug APK and passed unit tests, but failed four ktlint formatting checks.
- Commit `9cd1935` fixes those exact formatting failures.
- Run #29 (`33827487599`) on `9cd1935` completed successfully: ktlint, unit tests, debug APK build, and artifact upload all passed.

## Completed fork work

- S0/S0.5 fork foundation and fork-safe CI.
- Fork-owned Trakt OAuth credential injection with secret-less CI placeholders.
- Floppy connection settings and validation using `X-API-Key`.
- Trakt history to Floppy movie/episode history mirroring with rewatch timestamps, checkpoints, and retry-safe deduplication.
- Runtime API credential overrides for Trakt/TMDB.
- Free widget and quick-rating functionality restored.
- Free Light/System themes restored.
- Free Compact/Grid list view modes added.

## Validation still requiring a device/account

- S0.75: end-to-end Trakt login using a GitHub-built APK.
- S1: Floppy settings screen against a real user API token.
- S2: on-device bootstrap test against the configured Floppy account. GitHub CI verification for the current integrated branch is tracked above.

## S3 active design: watchlist mirroring

The first S3 slice is Showly local watchlist -> Floppy `Planning` consumptions for movies and TV shows using TMDB identity.

Safety rules:

1. Never use `DELETE /media/{type}/{source}/{id}` for watchlist removal because it deletes all consumptions/history for that title.
2. A matching existing Floppy `Planning` consumption satisfies the add operation and must not be duplicated.
3. Only a `Planning` consumption created by this Showly installation may be deleted when an item leaves Showly's watchlist.
4. Ownership is stored locally as TMDB id -> Floppy `consumption_id`. Existing/manual Floppy planning rows are never claimed or deleted.
5. If an owned Floppy row is edited to another status or disappears, drop ownership instead of deleting or rewriting it.
6. If Showly loses ownership state (for example after reinstall), prefer leaving a stale planning row over risking deletion of user-owned Floppy data.
7. Floppy failures remain non-fatal to normal Trakt synchronization.

The API contract was rechecked against `dannyvfilms/Floppy` latest head `5615a974edcdff2b3824e3a0db9fb1232b3267f7`. Relevant semantics:

- `POST /api/v1/media/{media_type}/` appends a new consumption; omitted status defaults to Planning.
- `GET /api/v1/media/{media_type}/tmdb/{media_id}/` returns exact consumptions including `consumption_id` and numeric status (`0` = Planning).
- `DELETE /api/v1/media/{media_type}/tmdb/{media_id}/history/{consumption_id}/` deletes one exact consumption and is the only removal route intended for this slice.

## Current S3 implementation state

The watchlist slice is implemented in the working tree:

- `data-remote` now stores per-media-type ownership as `TMDB id -> consumption_id`, resets it when Floppy instance/account identity changes, reuses existing Planning rows, creates explicit status `0` Planning rows when needed, and deletes only an owned exact history row after confirming it is still Planning.
- `FloppyWatchlistSyncRunner` reads the existing local Showly watchlist repositories, filters to valid TMDB ids, reconciles additions plus owned removals, and never claims an existing/manual Floppy Planning row.
- `TraktSyncWorker` invokes watchlist sync after the existing Floppy history sync and catches Floppy failures independently so normal Trakt synchronization can still succeed.
- Focused `data-remote` tests cover identity-change detection and ownership encoding/decoding.
- Targeted ktlint 1.5.0 passes for all modified Kotlin files.

## Immediate next steps

1. Commit and push the S3 watchlist implementation using the Codex author/committer identity.
2. Verify the new Fork CI run, including `data-remote` unit tests and debug APK artifact publication.
3. Record the new commit, run id, artifact status, and any corrections in this handoff.
4. Perform the still-pending on-device Trakt login, real Floppy connection, S2 bootstrap, and S3 watchlist add/remove validation when device/account validation is available.
5. After the watchlist slice is green, design the ratings mapping before writing it; do not infer score/deletion semantics ad hoc.

## Do not change without an explicit new design

- Trakt remains supported and remains Showly's mature synchronization path.
- Local canonical identity remains Trakt-based for now; provider-neutral database migration is deferred.
- Floppy integration boundaries prefer TMDB/TVDB/IMDb plus season/episode coordinates, never Floppy internal item ids.
- Do not make Floppy-originated changes flow back into Showly/Trakt until S4 conflict and deletion semantics are designed.
