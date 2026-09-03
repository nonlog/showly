# Product Direction

## Goal

Add a self-hosted Ryot tracking backend to Showly while keeping the fork maintainable against `trakt/showly`.

The long-term product should let a user keep watch history, watchlist, ratings, lists, and related personal tracking data in a Ryot instance instead of requiring Trakt to be the sole source of truth.

## S0/S1 scope

The first implementation stages are intentionally narrow:

- preserve existing Showly behavior and local data;
- keep Trakt available as a first-class backend using a fork-owned Trakt OAuth application;
- add Ryot as an optional tracking backend;
- configure a self-hosted Ryot base URL and credentials/integration details;
- verify connectivity before enabling sync;
- add tracking operations incrementally, starting with watched state/history;
- keep GitHub Actions as the canonical build and verification environment.

## Non-goals for the first stages

- Removing Trakt from the application.
- Replacing every catalog/discovery endpoint with TMDB/TVDB immediately.
- Renaming all Trakt-specific classes and UI in one refactor.
- Migrating the existing Room database away from Trakt IDs in the first patch.
- Reimplementing the Trakt API or pretending Ryot is a Trakt-compatible server.
- Depending on Ryot internal database IDs in Showly's domain model.

## Product principles

1. **Upstream compatibility first.** Changes should be isolated and small enough that upstream merges remain practical.
2. **User data ownership.** Ryot should be able to become the authoritative tracking store without making the Android app dependent on one hosted service.
3. **Provider-neutral boundaries.** New code should express tracking operations rather than Ryot-specific UI/business concepts where practical.
4. **External identity over server-internal identity.** Prefer TMDB, TVDB and IMDb identifiers, plus season/episode coordinates, at integration boundaries.
5. **Local-first UX remains intact.** Network failures must not make ordinary browsing or local state unusable.
6. **No destructive migration without a dedicated stage.** The current database is deeply keyed by Trakt IDs; replacing that identity model is a separate project milestone.

## Initial acceptance criteria

S1 is complete when a clean GitHub build can:

- show an optional Ryot configuration entry;
- save a self-hosted server URL securely enough for the existing app model;
- validate the server/authentication path;
- report a clear connected/error state;
- leave all existing Trakt and local behavior unchanged when Ryot is disabled.
