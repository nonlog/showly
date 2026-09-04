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

## Trakt ↔ Floppy bridge

Showly is the synchronization coordinator between Trakt.tv and Floppy. Neither provider is globally authoritative. For a shared mutable value, the provider with the newest mutation wins and Showly writes that value to the older side.

```text
Trakt.tv  ⇄  Showly bridge ledger/resolver  ⇄  Floppy
                    ⇅
              Showly local DB
```

### Conflict clock

The bridge uses the strongest timestamp available for each domain:

1. exact item/field timestamps, such as Trakt `listed_at` and `rated_at` or Floppy `changes_history.history_date`;
2. Trakt domain activity timestamps when an item disappeared and Trakt does not expose a per-delete timestamp;
3. Showly observation time for a transition when the provider exposes no mutation timestamp.

The bridge never interprets a first observation of absence as a deletion. Once a value has been observed, a later disappearance creates a tombstone with its mutation/observation time. A newer re-add can therefore beat an older tombstone. Equal values converge without a winner. Exact timestamp ties preserve the previous resolved value when possible, then use Trakt only as a deterministic tie-breaker.

Bridge state is stored in the local `bridge_sync_state` Room table. The ledger is scoped to a fingerprint of the current Trakt account plus Floppy endpoint/account identity. If that remote identity changes, the ledger is cleared so tombstones from one account cannot delete data in another.

### History and rewatches

History is modeled as an event set, not as one scalar watched flag. An event key contains the provider-neutral media identity plus exact watched instant:

- movie: TMDB id + watched instant;
- episode: parent-show TMDB id + season + episode + watched instant.

Each rewatch is independent. On bootstrap, an event present on only one provider is copied to the other. After both sides have been observed, removing an event produces a tombstone; if that deletion is newer than the other side's presence, it is removed there too. Re-adding the same event later can resurrect it.

Floppy exact-consumption deletion is used for movie plays and episode consumptions; broad media-level delete is never used for history reconciliation.

### Watchlist

The bridge maps Trakt movie/show watchlist membership to Floppy `Planning` state using TMDB identity. Trakt `listed_at` is the exact add timestamp; Trakt last-activity supplies the deletion clock. Floppy uses `created_at` for initial Planning rows and `changes_history` status mutations for transitions.

A newer add restores the older side; a newer removal clears the older side. Showly local watchlist state is updated to the resolved result. If TMDB→Trakt identity cannot be resolved, the unsynchronized side remains recorded as absent so a later sync retries instead of falsely marking convergence.

### Ratings

Trakt exposes a title-level integer rating from 1 to 10. Floppy stores score on consumption records, so the bridge defines an explicit projection rather than pretending the data models are identical:

- the latest Floppy `score` field mutation from `changes_history` is the Floppy title-rating projection;
- Trakt `rated_at` is the Trakt mutation timestamp;
- a newer value or rating removal overwrites the older side;
- fractional Floppy scores are preserved in Floppy while their Trakt projection is rounded to the nearest 1-10 integer;
- changing a score on Floppy does not change the row's tracking status;
- if a title has no Floppy tracking row, a Trakt rating is written as a score-only row with explicit `status: null`, never the API's implicit `Planning` default.

### Failure semantics

Bridge execution remains non-fatal to Showly's mature Trakt worker. However, the ledger is updated only for writes that actually completed (or are already satisfied). A mapping/network failure therefore remains visible as divergent state and is retried on the next bridge run.

## Custom lists: migration in progress

The last verified S3 implementation is additive-only: Showly-created lists and TMDB movie/show memberships can be added to Floppy, but destructive list/member reconciliation was deliberately disabled because Floppy `list_item_id` is a renumbered position, not an immutable relation id.

S4 changes the safety model. Custom lists will use current-state snapshots plus the bridge ledger/tombstones, so deletion authority comes from a newer observed mutation rather than from stale ownership of a relation id. Until that migration is complete, custom-list deletion remains disabled.

Floppy list membership still hydrates missing TMDB metadata through the non-tracking `/media/{type}/tmdb/{id}/sync/` route, so adding a title to a list does not silently change watch status.
