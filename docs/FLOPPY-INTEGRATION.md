# Floppy Integration Contract

## Status

Floppy is the optional self-hosted tracking backend for this fork. Trakt.tv remains supported and continues to use the existing Showly OAuth/sync implementation with a fork-owned Trakt application.

The S1 contract was verified against Floppy v26.8.27 and its reviewed OpenAPI document at /api/openapi.yaml.

## Authentication

The stable API supports user authentication with either Bearer auth or the X-API-Key header. Showly uses X-API-Key for the Floppy integration.

S1 connection validation uses:

- GET /api/v1/info/ without authentication for reachability;
- GET /api/v1/user/preferences/ with X-API-Key for authenticated account validation.

The API key is runtime user configuration. It must never be committed, included in analytics/crash reports, or written by the debug HTTP logger.

## Configuration

Showly stores:

```text
Floppy enabled
Floppy base URL
Floppy user API key
```

The base URL must use HTTP or HTTPS, contain a host, and must not contain embedded credentials, a query, or a fragment. Trailing slashes are normalized away.

## Connection states

- Disabled: Floppy integration is switched off.
- Not tested: configuration changed but has not been validated.
- Connected: both reachability and authenticated preference requests succeed.
- Unauthorized: Floppy is reachable but the API key is missing/rejected.
- Unreachable: network/service request fails or the server returns an unexpected status.
- Invalid configuration: the base URL fails local validation.

## Tracking scope after S1

The reviewed /api/v1 contract is suitable for the planned vertical slices: search/details, consumption/history, episode watched state, ratings, and list operations. New code should depend on the reviewed contract rather than undocumented internal endpoints whenever possible.

## Trakt to Floppy bridge

The intended first synchronization direction is:

```text
Trakt.tv
   ↓ existing Trakt sync
Showly local model
   ↓ Floppy adapter
Floppy
```

This lets the fork preserve mature Trakt synchronization while adding a self-hosted copy. A later stage may add Floppy-originated changes flowing back to Trakt, but only after conflict and deletion semantics are defined.

## S2 history synchronization

S2 mirrors the actual Trakt history event stream rather than reconstructing history from Showly's local watched flags. This preserves every rewatch event and its original watched_at timestamp.

Movie and episode history maintain separate Trakt history-id checkpoints. Trakt history pages are still scanned to completion because the API is ordered by watched time: a newly added backdated event can have a newer history ID on an older page. The checkpoint is therefore used as a local event filter, not as an unsafe early-pagination stop. It advances only after an event is successfully written, already present, or cannot be mapped because required external identity is missing. A Floppy/network failure leaves the current event uncheckpointed so the next Trakt sync retries it.

Before writing, Showly reads the stable Floppy media detail response and compares existing consumptions[].end_date with the Trakt watched_at instant. A malformed successful detail response fails closed and is retried instead of being treated as an empty history. Equivalent instants with different timezone offsets are treated as the same watch. This makes retries idempotent even if the app stops after the Floppy write but before persisting the checkpoint.

Changing the configured Floppy base URL or API key clears the history checkpoints. A new instance/account therefore bootstraps from Trakt history again, while consumption-time deduplication protects an existing account after token rotation.

Floppy synchronization is deliberately non-fatal to Trakt synchronization. Deletion/tombstone propagation is deferred to the bidirectional-sync milestone.

## S3 watchlist synchronization

The first S3 slice mirrors Showly's local movie/show watchlist to Floppy `Planning` consumptions using TMDB identity. It runs after normal Trakt import/export so the local watchlist remains the source for this one-way bridge.

Watchlist writes are deliberately ownership-aware. Before adding a title, Showly reads the Floppy media detail and treats any existing `Planning` consumption as already satisfied. When Showly must create a new `Planning` consumption, it stores only the resulting `consumption_id` alongside the TMDB id in local preferences. This ownership state is not a new canonical media identity; it exists only to make later deletion safe.

When a title leaves Showly's watchlist, Showly deletes only the exact Floppy history row that this installation previously created, and only after re-reading the detail response and confirming that row still has numeric status `0` (`Planning`). It never uses the media-level delete endpoint, because that endpoint deletes all consumptions/history for the title. Existing/manual Floppy planning rows are not claimed and are therefore never deleted by Showly.

If the owned row was edited to another status, disappeared, or local ownership state was lost, Showly drops or lacks ownership and preserves the Floppy data. This intentionally prefers a harmless stale planning row over destructive guessing. Changing the configured Floppy base URL or API key clears watchlist ownership together with history checkpoints because the account/instance identity may have changed.

Floppy watchlist synchronization is non-fatal to the Trakt sync worker, matching the S2 history policy.

## S3 custom-list synchronization

Showly mirrors local movie/show custom lists into Floppy without treating same-name remote lists as equivalent. Each Floppy list created by Showly is recorded as a local Showly list id -> Floppy list id ownership mapping. While that local list exists, the bridge may update the owned Floppy list's name, description, and visibility. Floppy only exposes a public/private flag, so Showly `public` maps to public while both `private` and `friends` map conservatively to private. A pre-existing Floppy list with the same name remains independent.

S3 list membership is intentionally additive-only. Current Showly movie/show items are added by TMDB identity; HTTP 409 simply means the membership already exists. Local member removal is not propagated to Floppy in S3. Local list deletion also does not delete the remote Floppy list: Showly only releases its local ownership mapping and leaves the remote list intact. This avoids deleting user edits or memberships that may have been added in Floppy after the mirror list was created.

This conservative rule is required by the current Floppy contract. The exposed `list_item_id` is a sequential list position and is renumbered after deletions, so it is not an immutable ownership token. Without a stable relation identity or conflict/tombstone policy, destructive reconciliation cannot distinguish an original Showly-created relation from later user edits. Deletions are therefore deferred to S4, where conflict and tombstone semantics are explicitly planned.

Floppy list membership requires the provider `Item` metadata row to exist, but this does not require creating a tracking consumption. On an initial membership 404, Showly calls Floppy's non-tracking `POST /api/v1/media/{type}/tmdb/{id}/sync/` metadata route and retries the membership PUT. This lets lists contain catalog items without silently changing watch status.

Changing the configured Floppy base URL or API key clears list ownership together with the watchlist/history state. Missing ownership always fails safe: Showly may leave stale or duplicate remote lists after reinstall/account changes, but it will not guess that an arbitrary remote list belongs to this installation.

## S3 rating boundary

Generic movie/show rating mirroring is intentionally deferred. In the reviewed Floppy contract, `score` is a field on a consumption. The generic media PATCH updates the convenience/default tracked row and POST creates a new consumption, so copying a Trakt title rating through either route can alter watch-history semantics. Until Floppy exposes a safe title-level rating contract, or an explicit mapping policy is adopted, the bridge must not synthesize or rewrite consumptions merely to mirror ratings.
