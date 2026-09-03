# Product Direction

## Goal

Keep Trakt.tv as a first-class Showly backend while adding Floppy as an optional self-hosted tracking store.

The fork uses its own Trakt OAuth application identity. Floppy complements Trakt instead of impersonating it or replacing the existing Trakt API implementation.

## S1 scope

- preserve existing Showly local data and Trakt behavior;
- configure an optional Floppy base URL and user API key;
- validate a Floppy instance with its stable /api/v1 contract;
- report disabled, connected, unauthorized, unreachable, and invalid configuration states;
- keep GitHub Actions as the canonical build and verification environment.

## Later sync direction

After connectivity is proven, Showly can use its existing Trakt sync to refresh local state and mirror supported tracking changes from the local model into Floppy. Floppy-originated changes may be synchronized back only after conflict, timestamp, deletion, and duplicate-history rules are documented and tested.

## Non-goals for the first stages

- Removing Trakt from the application.
- Reusing upstream Showly Trakt credentials.
- Pretending Floppy is a Trakt-compatible server.
- Replacing every catalog/discovery endpoint immediately.
- Migrating the existing Room database away from Trakt IDs in S1.
- Depending on Floppy internal database IDs in Showly's domain model.

## Product principles

1. **Trakt stays first-class.** Existing OAuth and sync behavior remains intact.
2. **Self-hosting stays optional.** Floppy failures must not break normal local or Trakt use.
3. **External identity at boundaries.** Prefer TMDB, TVDB, IMDb, media type, season, and episode coordinates when talking to Floppy.
4. **Small upstream conflict surface.** Add isolated provider code rather than rewriting stable Trakt paths.
5. **No silent two-master sync.** Bidirectional sync ships only with explicit conflict semantics.
6. **GitHub-first development.** GitHub is source of truth and GitHub Actions is the canonical build/test environment.
