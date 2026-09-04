# Development Handoff

Last updated: 2026-09-04

## Active line

- Repository: `nonlog/showly`
- Active branch: `feat/runtime-credentials-free-features`
- Upstream baseline: `trakt/showly@ec897b65b1b55c18ce24a755f83f894f422e559a`
- Current verified GitHub head: `40532b012eedb8c229c03d5e4eb3b4eaa6a01b7e` (`feat: mirror watchlist to Floppy`)
- Commit identity for agent-created commits: `Codex <codex@openai.com>` for both author and committer.
- GitHub Actions `Fork CI` is the canonical validation environment.

## Current CI state

- Run #28 (`33774902942`) on `339f120` built and uploaded the debug APK and passed unit tests, but failed four ktlint formatting checks.
- Commit `9cd1935` fixes those exact formatting failures.
- Run #29 (`33827487599`) on `9cd1935` completed successfully: ktlint, unit tests, debug APK build, and artifact upload all passed.
- Run #30 (`33828070287`) on `40532b0` completed successfully: ktlint, selected unit tests, debug APK build, and artifact upload all passed.
- Run #30 artifact: `showly-debug-40532b012eedb8c229c03d5e4eb3b4eaa6a01b7e`, 15,376,997 bytes, SHA-256 `85fd19dd1ac621bc3fc248c1537684765dc5e18e9fa3274974ca93d40e663c04`.

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

The watchlist slice is verified in commit `40532b0`:

- `data-remote` now stores per-media-type ownership as `TMDB id -> consumption_id`, resets it when Floppy instance/account identity changes, reuses existing Planning rows, creates explicit status `0` Planning rows when needed, and deletes only an owned exact history row after confirming it is still Planning.
- `FloppyWatchlistSyncRunner` reads the existing local Showly watchlist repositories, filters to valid TMDB ids, reconciles additions plus owned removals, and never claims an existing/manual Floppy Planning row.
- `TraktSyncWorker` invokes watchlist sync after the existing Floppy history sync and catches Floppy failures independently so normal Trakt synchronization can still succeed.
- Focused `data-remote` tests cover identity-change detection and ownership encoding/decoding.
- Targeted ktlint 1.5.0 passes for all modified Kotlin files.

## Immediate next steps

1. Commit and push the implemented custom-list slice now that watchlist CI #30 is green.
2. Verify the custom-list commit in Fork CI and record its exact run/artifact status here.
3. Perform the still-pending on-device Trakt login, real Floppy connection, S2 bootstrap, S3 watchlist add/remove, and custom-list create/add/remove validation when device/account validation is available.
4. Keep ratings blocked until a safe title-level score contract or explicit consumption-selection policy exists.

## Ratings design finding

Do not implement generic Trakt rating mirroring yet. In the current Floppy API, `score` is stored on a consumption row rather than as an independent title-level rating. The generic media PATCH chooses the convenience/default tracked row (`user_medias[0]`), while POST creates a new consumption (defaulting to Planning when status is omitted). Either path can mutate tracking semantics merely to copy a rating, especially for rewatches. The dedicated score route currently exists for episodes but not as a symmetric movie/show title-level contract. S3 ratings therefore remains intentionally blocked until an explicit safe mapping is defined or Floppy exposes a title-level rating contract.

## S3 custom-list design

Custom lists can proceed safely because Floppy exposes independent list resources and media-list membership routes that do not create consumptions. The planned one-way mapping is:

- create a Floppy list for each local Showly list and store local Showly list id -> Floppy list id ownership;
- never claim an existing Floppy list by matching its name;
- update only an owned Floppy list's name, description, and public/private visibility;
- record membership ownership only when Showly successfully adds a media item; a pre-existing membership is left unowned;
- remove only memberships that Showly previously created;
- delete only Floppy lists that Showly previously created when the corresponding local list disappears;
- hydrate missing TMDB Item metadata through Floppy's non-tracking `/media/{type}/tmdb/{id}/sync/` route before retrying list membership, rather than creating a tracking consumption.

This keeps manual Floppy lists and manual membership changes outside Showly's deletion authority.

The custom-list slice is implemented in the working tree with a separate `FloppyListsRemoteDataSource`, `FloppyListsSyncRunner`, Hilt binding, identity-reset hooks, and pure ownership codec tests. Targeted ktlint 1.5.0 passes. Watchlist Fork CI #30 is now green, so this slice is ready to commit and push.

## Do not change without an explicit new design

- Trakt remains supported and remains Showly's mature synchronization path.
- Local canonical identity remains Trakt-based for now; provider-neutral database migration is deferred.
- Floppy integration boundaries prefer TMDB/TVDB/IMDb plus season/episode coordinates, never Floppy internal item ids.
- Do not make Floppy-originated changes flow back into Showly/Trakt until S4 conflict and deletion semantics are designed.
