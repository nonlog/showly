# Ryot Integration Contract

## Status

Design document for S0. Exact Ryot GraphQL operations and authentication details must be validated against the deployed Ryot version during S1 before production code is committed.

Ryot v10 is the current deployment line in the official documentation. Its project advertises a GraphQL API for custom integrations, and it also supports sink integrations under `/_i/<slug>` for services such as Emby. Movies and shows use TMDB and/or TVDB metadata providers.

Official references:

- https://docs.ryot.io/
- https://docs.ryot.io/configuration
- https://docs.ryot.io/integrations/overview.html
- https://docs.ryot.io/integrations/emby
- https://docs.ryot.io/guides/movies-and-shows
- https://github.com/IgnisDa/ryot

## Connection model

The Android fork should eventually store, at minimum:

```text
Ryot enabled
Ryot base URL
Authentication/integration credential
Last successful validation time
Optional sync policy
```

The UI should accept a base instance URL rather than hard-coding a deployment host.

Secrets/tokens must never be committed to the repository, emitted into CI logs, or copied into analytics/crash reports.

## API choice

S1 must test the available approaches before fixing the client contract:

1. **Authenticated GraphQL** for bidirectional user-data operations when stable mutations/queries are available.
2. **Ryot sink integration** for one-way playback/progress ingestion where the sink semantics fit the operation.

Showly needs reads as well as writes, so a sink alone cannot satisfy the complete product. Do not build a fake Trakt compatibility layer.

## Required capability matrix

| Capability | Priority | Direction | S0 status |
| --- | --- | --- | --- |
| Connection/account validation | P0 | read | API to validate in S1 |
| Movie watched/history | P0 | read/write | API to validate in S1 |
| Episode watched/history | P0 | read/write | API to validate in S1 |
| Watchlist | P1 | read/write | API to validate |
| Ratings | P1 | read/write | API to validate |
| Personal lists | P2 | read/write | API/data-model fit to validate |
| Collections | P2 | read/write | semantics to define |
| Comments/notes | P3 | read/write | not part of first vertical slice |
| Hidden/dropped state | P3 | read/write | semantics to define |

## Identity mapping

Integration requests should be constructed from external identifiers already present in Showly rather than from a Ryot database row ID.

Preferred order for matching:

- movie: TMDB, then IMDb/TVDB where supported;
- show: TMDB or TVDB, with IMDb as fallback;
- episode: parent show external ID + season + episode, plus episode TVDB/IMDb when available.

Known Ryot/Emby behavior reinforces this rule: Ryot's Emby integration requires valid TMDB metadata, and show progress can require the show to already exist in Ryot because the Emby webhook does not provide the expected show TMDB ID.

## Error and offline behavior

- A Ryot outage must not prevent a local Showly action from completing.
- Network errors are retryable; authentication/validation failures are surfaced as configuration errors.
- Writes should become idempotent or deduplicated before background retry is enabled.
- Failed sync state must be observable; never discard a queued operation silently.
- Backoff must avoid tight retry loops against self-hosted instances.

## Conflict policy

Conflict resolution is intentionally deferred until read/write primitives are proven. The eventual policy must define:

- which backend is authoritative when both Trakt and Ryot are enabled;
- timestamp semantics (`watched_at`, rating update time, list update time);
- deletion/tombstone handling;
- initial bootstrap versus ongoing incremental sync;
- how duplicate history plays are represented.

No two-way sync should ship before these rules are documented and tested.
