# Roadmap

## S0 - Fork initialization and architecture discovery

- [x] Create `nonlog/showly` as a real GitHub fork of `trakt/showly`.
- [x] Record upstream baseline and default branch.
- [x] Audit Trakt remote, repository, sync, identity and local-storage coupling.
- [x] Define product scope and non-goals.
- [x] Define staged identity/provider direction.
- [x] Add agent/upstream maintenance rules.
- [ ] Verify documentation against the first deployed Ryot instance during S1.

No business logic changes belong to S0.

## S0.5 - Fork-safe GitHub CI

- Add PR/feature-branch verification.
- Remove the need for upstream signing/decryption secrets from debug/test builds.
- Run lint and existing unit-test suites.
- Produce a debug APK artifact from GitHub Actions.
- Keep release signing isolated in protected secrets/workflows.

## S1 - Minimal Ryot connectivity

- Add Ryot configuration state.
- Add base URL validation and normalized URL handling.
- Validate the deployed v10 authentication/API contract.
- Implement connection/account test.
- Add tests for disabled, connected, unauthorized and unreachable states.

## S2 - Watched/history vertical slice

- Introduce the minimum tracking adapter boundary required by real call sites.
- Write movie watched/history to Ryot.
- Write episode watched/history to Ryot.
- Preserve existing local/Trakt behavior when Ryot is disabled.
- Add retry/idempotency tests.

## S3 - Read sync and library state

- Read watched/history from Ryot.
- Add watchlist read/write.
- Add ratings read/write.
- Define initial bootstrap behavior.

## S4 - Background synchronization and conflicts

- Define source-of-truth modes.
- Define timestamp/deletion/duplicate-history semantics.
- Add durable queue/retry behavior for Ryot.
- Add manual and background sync UX.

## S5 - Additional user data

Evaluate lists, collections, hidden/dropped state, notes/comments and any Ryot-specific semantic differences. Implement only capabilities that map cleanly.

## S6 - Deeper decoupling (optional)

Only after the Ryot tracking path is stable:

- evaluate replacing Trakt catalog/discovery calls with other providers;
- evaluate a provider-neutral local media key;
- design and test Room migrations if Trakt ID is no longer acceptable as the local key;
- rename legacy Trakt-specific UI/classes where doing so materially reduces maintenance cost.
