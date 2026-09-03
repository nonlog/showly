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
