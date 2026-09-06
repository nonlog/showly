# Development Handoff

Last updated: 2026-09-05

## Active line

- Repository: `nonlog/showly`
- Active branch: `feat/runtime-credentials-free-features`
- Upstream baseline: `trakt/showly@ec897b65b1b55c18ce24a755f83f894f422e559a`
- Latest fully verified code head: `00462bc401fc551dae669a200224a455851f798a` (`fix: persist episode bridge identity`).
- Any later `[skip ci]` handoff-only commit does not change the verified code baseline.
- Commit identity for agent-created commits: `Codex <codex@openai.com>` for both author and committer.
- GitHub Actions `Fork CI` is the canonical validation environment.

## Current CI state

- Run #28 (`33774902942`) on `339f120` built and uploaded the debug APK and passed unit tests, but failed four ktlint formatting checks.
- Commit `9cd1935` fixes those exact formatting failures.
- Run #29 (`33827487599`) on `9cd1935` completed successfully: ktlint, unit tests, debug APK build, and artifact upload all passed.
- Run #30 (`33828070287`) on `40532b0` completed successfully: ktlint, selected unit tests, debug APK build, and artifact upload all passed.
- Run #30 artifact: `showly-debug-40532b012eedb8c229c03d5e4eb3b4eaa6a01b7e`, 15,376,997 bytes, SHA-256 `85fd19dd1ac621bc3fc248c1537684765dc5e18e9fa3274974ca93d40e663c04`.
- Runs #31 (`33828590003`) and #32 (`33828665265`) were intentionally superseded/cancelled by follow-up custom-list safety fixes.
- Run #33 (`33828952968`) on `16651c8` completed successfully: ktlint, selected unit tests, debug APK build, and artifact upload all passed.
- Run #33 artifact: `showly-debug-16651c82cdac028009f2d9c39b0ad702be8c4dde`, 15,391,339 bytes, SHA-256 `b01f638bf066b78565b1bd1078a8a52fb2e1ab1a430802cf331b800c6bdd1d05`.
- The extracted #33 APK is 17,189,924 bytes with SHA-256 `9379413335e2cd15e4113e9d8c4186b74f7548342f57992ddf2a0971d29704eb`. On 2026-09-04 it was installed successfully on the connected CPH2573 device as `com.michaldrabik.showly2.debugoss` (`versionCode=923`, `versionName=3.58.1-debug`); the production `com.michaldrabik.showly2` package remained at `3.70.0`.
- Run #34 (`33833226001`) on `b918fed` failed only at Android resource compilation because the new helper string contained an unescaped apostrophe.
- Run #35 (`33833367879`) on `dd2d5b0` passed resources/lint and reached Kotlin compilation; it exposed three History bridge compile errors that were corrected in `7654a40`.
- Run #36 (`33833681276`) on `7654a40` completed successfully: ktlint, selected unit tests, Debug APK build, and artifact upload all passed. This is the canonical verified S4 kernel baseline before the Custom Lists migration is pushed.
- Run #37 (`33845655962`) on `89fe98e` completed successfully: ktlint, selected unit tests, Debug APK build, and artifact upload all passed. This is the canonical verified S4 baseline including bidirectional Custom Lists.
- Run #37 artifact: `showly-debug-89fe98ea83c3aa874ac56c7071571cbbd7e3414e`, 15,538,325 bytes, SHA-256 `adffdeb37d01bc82d760d4238920b4131588710dbc5824d3571fc3b51cc54382`. The extracted APK is 17,339,170 bytes with SHA-256 `1637400d567d9fae08602790472bd41877e217fc2b78897f363d7e9d7b6d03b1`.
- On 2026-09-04, the #37 APK was installed successfully on CPH2573 as `com.michaldrabik.showly2.debugoss` (`versionCode=923`, `versionName=3.58.1-debug`). Production `com.michaldrabik.showly2` remained unchanged at `3.70.0` (`versionCode=840`). Temporary transfer files were removed from the device.
- Read-only device validation after the #37 install confirmed the saved Trakt access/refresh-token entries are still present, the saved Floppy enable/base URL/API-key entries are still present, and the configured Floppy instance returns HTTP 200 for both `/api/v1/info/` and authenticated `/api/v1/user/preferences/`. No credential values were printed or stored in the handoff.
- Room opened at schema/user version 42 with the `bridge_sync_state` table present (10 columns). It had 0 bridge rows at the validation point, confirming no bidirectional reconciliation had been run yet. Temporary database-extraction files used for the schema check were removed.
- Run #38 (`33852569892`) on `3733905` completed successfully: ktlint, selected unit tests, Debug APK build, and artifact upload all passed. Artifact `showly-debug-3733905252779db1f5a0de5324016729e09a5cef` is 15,546,615 bytes with SHA-256 `76a42ae41908552ad0c5c884d8c644f259a9c91d67ca314d6e6c9d42b853607d`; the extracted APK is 17,346,818 bytes with SHA-256 `e5c2ae7e34ba85b441ff2be90ae464e07bb7330377571e057ce3e22682fe9240`. It was installed successfully on CPH2573 as the debug package; production Showly was not touched.
- Run #39 (`33853342095`) on `e7e93bc` exposed one ktlint-only expression-body formatting issue after the worker mutex change and was then superseded/cancelled by the formatting-only follow-up. No functional failure was observed.
- Run #40 (`33853551265`) on `15e2716` completed successfully: ktlint, selected unit tests, Debug APK build, and artifact upload all passed. Artifact `showly-debug-15e2716763249fefe97e40a1d3c2b08905218b83` is 15,546,446 bytes with SHA-256 `149cd90ff3ce842de362f1debe2899ad5b3f98dd839ab94ff818671b3c6f6943`; the extracted APK is 17,347,591 bytes with SHA-256 `872c095937b6c4bf5c4085547afd48047cafb082d991bd9c57a6f7cf134bba28`. It was installed successfully on CPH2573 as `com.michaldrabik.showly2.debugoss` (`versionCode=923`, `versionName=3.58.1-debug`); production Showly remains `3.70.0` (`versionCode=840`). Temporary transfer files were removed.
- Run #41 (`33854650796`) on `0d64c7f` completed successfully and verifies the expanded latest-wins conflict matrix: newer edits, deletions, re-adds in both directions, provider timestamps, observation-time fallback, exact ties, and first-absence protection.
- Run #42 (`33935929026`) on `a373abe` completed successfully: ktlint, selected unit tests (including `BridgeRetryRepositoryTest`), Debug APK build, and artifact upload all passed. Artifact `showly-debug-a373abe171f3f3a6210706c2d71e895ecf78a272` is 15,566,812 bytes with SHA-256 `5311b31e2da278b92a31eb4aac2de2fc6782cd8ce346f8be85db6f49939fa8ef`; extracted APK is 17,367,261 bytes with SHA-256 `336ff266174e7081e805e8b5795803c79b04ed94cc3a8766d3e5d4bd0d7b99a6`.
- The #42 APK was installed successfully on CPH2573 as `com.michaldrabik.showly2.debugoss` (`versionCode=923`, `versionName=3.58.1-debug`); production `com.michaldrabik.showly2` remained `3.70.0` (`versionCode=840`). The debug app was launched once without screenshots or a manual sync to let Room open the database; device verification then confirmed schema 43, `bridge_retry_state` present with columns `domain`, `queued_at`, `attempt_count`, `last_attempt_at`, `last_error`, and zero pending retry rows. Temporary APK/database extraction files were removed.
- Run #43 (`33937077077`) on `dea10fd` completed successfully and verified the debug-only, shell-permission-protected bridge QA trigger. Its APK SHA-256 is `dbbda91ff128baff26aaaba6f740de3287d7195067ce9893f2e5e22e447544ba`; it was installed on CPH2573 and used for the controlled live Custom Lists/Watchlist tests below. Production Showly remained untouched.
- Run #44 (`33938982250`) on `f39a391` completed successfully: ktlint, selected unit tests, Debug APK build, and artifact upload all passed. Artifact `showly-debug-f39a391b4aa86c10f76317116063848c3bc37449` is 15,567,234 bytes with SHA-256 `114c8e69cdb55fe6c3b97ae825a6c1c4e33b09a5dd5a4c1f458e650c05b94556`; extracted APK is 17,368,691 bytes with SHA-256 `af09d4777dccc89ea72799c863ee208faa41e80d73090008d35fbe6ac1e991e5`. It was installed on CPH2573 after ADB recovered, and the exact previously failing Trakt-side Watchlist deletion case passed live: Trakt remained absent and the Floppy Planning row was removed with zero bridge failures.
- Run #45 (`33939410106`) on `bdcd11c` completed successfully: ktlint, selected unit tests including the focused bridge tombstone export policy tests, Debug APK build, and artifact upload all passed. Artifact `showly-debug-bdcd11c1cb825bd98ee95822d27a1f375e1453b8` is 15,568,551 bytes with SHA-256 `88bfc106dd6f3412969fd93787dbeee2af67a7a868bac3647877c614a6faae99`; extracted APK is 17,370,859 bytes with SHA-256 `9a3bf44b5b9bb31143bad4623d890c78b400be0b1c64f528a69b3873628e2f92`. It is installed on CPH2573; production Showly remains unchanged.
- Run #46 (`33939987820`) on `7adf0f1` completed successfully: ktlint, focused `BridgePrepassPolicyTest`, selected unit tests, Debug APK build, and artifact upload all passed. This is the verified failure-safety baseline where a failed bridge pre-pass blocks only the matching legacy Trakt export while other domains continue.
- Run #47 (`33940334616`) on `a578bae` completed successfully: ktlint, selected unit tests including the focused Floppy rating write-action coverage, Debug APK build, and artifact upload all passed. Artifact `showly-debug-a578bae44c6c8ef30c48dbefb1466115b2e8bcf5` is 15,572,268 bytes with GitHub artifact digest `sha256:b9d1f16941d5267cad10fd990b2a57b1d2957b393300b853840c0c1098bcca6d`; extracted APK is 17,374,165 bytes with SHA-256 `cdfaed6573ce74aedf08157445ff71a41f5194a8c8c8ffc3929bc3e67cbb3191`. It was installed successfully on CPH2573 as `com.michaldrabik.showly2.debugoss` (`versionCode=923`, `versionName=3.58.1-debug`); production `com.michaldrabik.showly2` remains `3.70.0` (`versionCode=840`). Temporary device transfer files were removed.
- Run #48 (`33945849234`) on `ddb7fd7` completed successfully: ktlint, unit tests, schema-44 Room/Hilt compilation, Debug APK build, and artifact upload all passed. This verifies the first local History/Watchlist two-provider outbox implementation plus removal of the duplicate History/Watchlist post-pass and the Floppy History N+1 scan.
- Run #49 (`33946252530`) on `c0dc9d1` completed successfully after Fork CI was expanded to run `:data-local:testDebugUnitTest`; it verifies independent provider acknowledgements, shared bridge/QuickSync serialization, and pending preservation when TMDB identity cannot be resolved.
- Run #50 (`33946637834`) on `1fa4729` completed successfully: ktlint, all selected unit tests, Debug APK build, and artifact upload passed. This is the verified baseline where a manual/periodic full sync drains pending Showly-local History/Watchlist mutations to both providers before taking bridge snapshots.
- Run #53 (`33947537911`) on `00462bc` completed successfully: ktlint, all selected unit tests including the schema-44 provider-identity outbox coverage, Debug APK build, and artifact upload all passed. Artifact `showly-debug-00462bc401fc551dae669a200224a455851f798a` is 15,587,903 bytes with GitHub digest `sha256:bb7f5e6c2674572b4ab88c47fd186357585396141803551affc9695989e1c3ef`; extracted APK is 17,392,405 bytes with SHA-256 `c4633297892065c96e0bcb979f28acc03dc9b1f77d9e6d16a5950119720c3bb5`. It was installed successfully on CPH2573 as the debug package; production Showly remained unchanged.
- On-device schema-44 validation passed after the #53 install. The database opened at user version 44 with all 12 outbox columns (`trakt_done`, `floppy_done`, `media_tmdb_id`, `season_number`, `episode_number` included). The 43 -> 44 recovery seed automatically queued the two legacy local-only House of the Dragon episodes and QuickSync drained the queue without a full manual bridge run. The previously missing S1E1/S1E2 are now present exactly once in both Trakt and Floppy; S1E2 `last_exported_at` changed from NULL to a fresh export timestamp and the outbox returned to empty.
- A real full bridge timing re-test on #53 completed successfully with no failed domains in about 40.1 seconds (`KEY_LAST_FLOPPY_BRIDGE_ATTEMPT=1788587319599`, success `1788587359731`), down from the previous ~65.3 seconds (~39% faster). The History N+1 and duplicate History/Watchlist post-pass removals are therefore materially effective, but ~40 seconds is still longer than desired.

## Completed fork work

- S0/S0.5 fork foundation and fork-safe CI.
- Fork-owned Trakt OAuth credential injection with secret-less CI placeholders.
- Floppy connection settings and validation using `X-API-Key`.
- Trakt history to Floppy movie/episode history mirroring with rewatch timestamps, checkpoints, and retry-safe deduplication.
- Runtime API credential overrides for Trakt/TMDB.
- Free widget and quick-rating functionality restored.
- Free Light/System themes restored.
- Free Compact/Grid list view modes added.

## S4 active direction: Trakt ↔ Floppy bridge

The product direction changed on 2026-09-04: Showly is no longer a one-way Trakt -> Floppy mirror. It is the synchronization bridge between Trakt.tv and Floppy. For shared mutable data, the newest mutation wins and the older side is overwritten.

The bridge kernel, redesigned credentials sheet, and bidirectional Custom Lists migration are verified by Fork CI #37 at `89fe98e`.

Fork CI #38 verified the user-visible/manual bridge control surface at `3733905`: it records bridge attempt/success timestamps, the number of reconciliation changes, and failed domains separately from the mature Trakt worker result; the Floppy settings page exposes a `Sync Trakt ↔ Floppy now` action only when Trakt is authorized and the Floppy connection is healthy. The #38 APK was installed successfully on CPH2573.

Fork CI #40 verifies the two follow-up safety guards: all full `TraktSyncWorker` executions are serialized with an in-process mutex so periodic and manual runs cannot reconcile the same bridge ledger concurrently, and changing the saved Floppy endpoint/API key clears the visible last-run bridge status alongside the existing ledger/ownership reset. The #40 APK is installed on CPH2573.

Conflict rules now being implemented:

1. Use an exact provider timestamp when one exists: Trakt `listed_at` / `rated_at`, Trakt last-activity timestamps for domain deletions, and Floppy `changes_history.history_date` for status/score changes.
2. If an API exposes the state change but no per-item mutation timestamp, stamp the transition when Showly observes it. This is the tombstone time used for future latest-wins comparisons.
3. The first time an item is absent is only an observation, never an inferred deletion. A deletion exists only when a previously observed value disappears.
4. If both sides have the same value, converge without forcing a winner. Exact timestamp ties preserve the previously resolved value when possible; otherwise Trakt is the deterministic final tie-breaker.
5. Bridge state is persisted in Room (`bridge_sync_state`, schema 42). It stores values/timestamps only. A SHA-256 fingerprint of Trakt account + Floppy endpoint/account credential identity guards the ledger; a remote identity change clears the ledger instead of applying stale tombstones to a different account.
6. Bridge failures remain non-fatal to the existing Trakt worker, but a failed side is not falsely recorded as synchronized so it can retry.

Implemented in the current working tree:

- **Watchlist:** movie/show `Planning` presence is reconciled in both directions by TMDB identity. Trakt `listed_at`, Trakt watchlist activity, Floppy `created_at`, and Floppy status change history feed the conflict clock.
- **History / rewatches:** each exact movie or episode watch instant is an independent event. Trakt and Floppy event sets are reconciled both ways. A later observed deletion becomes an event tombstone; a later re-add can resurrect the event. Independent rewatches are not collapsed.
- **Ratings:** movie/show ratings are reconciled both ways. Floppy's latest `score` mutation is treated as the title-level bridge projection; Trakt's 1-10 integer scale is the common projection, so fractional Floppy scores are rounded only when exported to Trakt. Writing a rating to an untracked Floppy title uses an explicit `status: null` row so rating sync does not create a `Planning` watch state.
- **Credentials UI:** the old oversized `MaterialAlertDialog` has been replaced with a Showly-styled expanded bottom sheet with Trakt/TMDB sections, field-level Trakt-pair validation, a primary save action, and a quiet restore-default action. The user visually confirmed this UI is now correct on-device on 2026-09-05.
- **Showly-originated mutations (working tree after #47):** the previous S4 implementation still treated Showly local state mostly as a cache and depended on the legacy Trakt QuickSync/export path. This meant a local History/Watchlist mutation could remain local when the legacy Trakt quick-sync toggle was off, and Floppy had no direct local fast path. The schema-44 working tree promotes `trakt_sync_queue` into a two-provider durable outbox for movie/episode history and movie/show watchlist mutations. Each row now has independent `trakt_done` and `floppy_done` acknowledgements; a provider failure cannot discard the other provider's pending write. Local removals are explicit `REMOVE` mutations. Bridge-enabled shared-domain writes schedule regardless of the legacy Trakt quick-sync toggles, while unrelated hidden/list behavior keeps its existing settings semantics.
- **Quick-sync failure safety (working tree after #47):** Floppy and Trakt are attempted independently. Successful provider acknowledgements are durable and completed rows are deleted only after both providers acknowledge them. Generic failures use WorkManager exponential retry; a failure on one side no longer prevents the other side from receiving the local mutation. Local QuickSync now shares `BridgeSyncExecutionGate` with full bridge/retry workers to prevent concurrent remote/ledger mutation. A missing TMDB identity is left pending by failing the Floppy acknowledgement rather than being falsely marked complete.
- **Full-sync ordering (follow-up working tree):** a manual/periodic full sync drains any pending local History/Watchlist outbox rows to both remotes before the bridge takes remote snapshots. If either provider acknowledgement remains pending, full remote reconciliation stops instead of allowing stale remote state to overwrite a just-made Showly mutation. This closes the race where the user adds locally and immediately presses `Sync Trakt ↔ Floppy now` before the delayed QuickSync worker runs.
- **Full-sync performance (working tree after #47):** History and Watchlist no longer repeat a second full bridge post-pass after the legacy Trakt import/export phase. Floppy History also no longer performs the old flat-catalog + one-detail-request-per-identity N+1 scan: the fork's `flat=1` history payload already exposes `instance_id`, `played_at_local`, parent TMDB `media_id`, and episode coordinates, so the bridge constructs exact movie/episode events directly from paginated flat history. Ratings and Custom Lists still retain their post-pass until equivalent local fast paths are migrated.
- **Full-sync performance follow-up (working tree after #53):** each bridge runner currently calls `validateConnection`, which costs two Floppy HTTP requests (`/info/` + authenticated `/user/preferences/`); one full run can repeat this many times. The next patch adds a 60-second in-memory validation cache keyed by the exact Floppy config while keeping the Settings “Test connection” action forced/fresh. A successful Watchlist bridge pre-pass also makes the mature Watchlist import/export duplicate work, so that duplicate path is skipped only when the pre-pass succeeded; if the bridge pre-pass failed, mature import still runs and legacy export stays blocked by the existing safety policy. The unconditional 1.25-second wait before hidden-item export is also removed when the watched exporter has already completed its own rate-limit pacing.

Custom-list migration verified at `89fe98e`:

- **List identity/bootstrap:** existing Showly-owned Floppy mappings are retained. An unpaired Trakt list adopts an unpaired Floppy list only when the shared metadata projection (name, description, public/private) has exactly one match; otherwise the missing counterpart is created. Unpaired Floppy lists get a Trakt/local counterpart instead of being ignored.
- **List presence:** list creation/deletion now participates in the bridge ledger. First absence remains non-destructive; after a pair has been observed, a newer deletion on either provider deletes the older counterpart and local row.
- **List metadata:** name, description, and public/private projection use latest-wins. Trakt exposes `updated_at`; Floppy does not expose a metadata-edit timestamp, so an observed metadata transition is timestamped when Showly sees it. Trakt `friends` projects conservatively to private on Floppy.
- **List membership:** movie/show membership uses TMDB identity. Trakt additions use `listed_at`; Floppy additions/removals without item-level mutation timestamps use observation time. Newer membership add/remove is propagated both ways, and Showly local membership follows the resolved state. The old `list_item_id` ownership workaround is no longer used as deletion authority.
- **Failure safety:** identity lookup/network failures keep the unresolved side divergent in the ledger, so a later sync retries instead of falsely declaring convergence. The list bridge is deliberately invoked both before and after the mature Trakt list import/export path: the pre-pass observes remote deletion before the legacy exporter can recreate it, and the post-pass propagates local→Trakt edits to Floppy in the same worker run. Member tombstones recover TMDB identity from the persisted ledger key, so a member deleted independently on both providers still converges to absence.

Additional Floppy-only data (notes, playback progress, hidden/dropped semantics) stays outside the bridge until a clean Trakt mapping exists.

Fork CI #42 verifies the durable retry layer:

- Room schema 43 adds `bridge_retry_state`, a durable per-domain retry queue with queued time, attempt count, last attempt, and sanitized last-error class.
- A dedicated `FloppyBridgeRetryWorker` retries only failed bridge domains with WorkManager network constraints and exponential backoff instead of waiting for the next full Trakt sync. Automatic retries stop after four attempts while leaving the queue durable for the next manual/periodic run.
- Full Trakt sync and bridge retry execution share one in-process mutex, so they cannot mutate the bridge ledger concurrently.
- Successful full reconciliation clears stale retry entries; remote identity changes clear both the bridge ledger and retry queue.
- The Floppy settings summary reads pending domains from Room so failures are visible as `history`, `watchlist`, `ratings`, or `lists` rather than only a generic last-run failure.
- The #42 APK has also exercised the real on-device Room 42 -> 43 migration successfully.

## Validation still requiring a device/account

- S0.75: end-to-end Trakt login using a GitHub-built APK.
- S1: Floppy settings screen against a real user API token.
- S2: on-device bootstrap test against the configured Floppy account. GitHub CI verification for the current integrated branch is tracked above.
- Device install baseline: CI #53 debug APK is installed and has passed schema-44 recovery plus real Trakt/Floppy verification for the previously missing S1E1/S1E2 history. Production Showly remains untouched.

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

1. Verify the post-#53 full-sync request-reduction patch (Floppy connection-validation reuse, duplicate mature Watchlist skip after a successful bridge pre-pass, and redundant watched-export delay removal) in Fork CI, install it, then re-measure against the #53 40.1 s baseline.
2. Validate one new local Watchlist add/remove and one new unfollowed-show episode add/remove with legacy `trakt_quick_sync_enabled=0`; the #53 migration/recovery path already proved the two-provider outbox itself works and delivered the user's previously missing S1E1/S1E2 to both remotes.
3. If full sync is still materially above ~30 seconds, profile the remaining Ratings/Custom Lists mature+bridge duplication before changing semantics.
4. Resume the Ratings/History deletion/re-add conflict matrix after the local-origin regression and performance work are closed.

## Historical S3 rating finding (superseded by S4)

S3 intentionally did not implement generic Trakt rating mirroring. In the current Floppy API, `score` is stored on a consumption row rather than as an independent title-level rating. The generic media PATCH chooses the convenience/default tracked row (`user_medias[0]`), while POST creates a new consumption (defaulting to Planning when status is omitted). Either path can mutate tracking semantics merely to copy a rating, especially for rewatches. The dedicated score route currently exists for episodes but not as a symmetric movie/show title-level contract. S4 now supplies that explicit mapping: the bridge treats the latest Floppy `score` mutation as the title-level projection, never changes status while updating an existing score, and creates a score-only Floppy row with explicit `status: null` when no tracking row exists.

## S3 custom-list design

Custom lists use an additive-only one-way mirror in S3:

- create a new Floppy list for each local Showly list and store local Showly list id -> Floppy list id ownership;
- never claim an existing Floppy list by matching its name;
- update only an owned Floppy list's name, description, and visibility while the local list exists; Showly `public` maps to public, while `private` and `friends` map to private because Floppy has no friends-only equivalent;
- add current movie/show members by TMDB identity; an existing membership (HTTP 409) is simply accepted;
- hydrate missing TMDB Item metadata through Floppy's non-tracking `/media/{type}/tmdb/{id}/sync/` route before retrying list membership, rather than creating a tracking consumption;
- do not propagate local member deletion in S3;
- when a local list disappears, release the local ownership mapping but do not delete the remote Floppy list.

The reason for the additive-only rule was verified directly in Floppy's current list model: `list_item_id` is a sequential list position and later rows are renumbered whenever one row is deleted. It is therefore not a stable relation identity. Even storing it locally cannot prove that a later relation with the same media/list pair is still the exact relation Showly originally created. Automatic list/member deletion would risk deleting user edits, so destructive list reconciliation is deferred to S4 conflict/tombstone design.

The custom-list slice was introduced in `26a3995`; `c4fdf30` fixed the suspend item resolver call shape. Safety review then identified that Floppy `list_item_id` is renumbered, so `16651c8` removed destructive member/list reconciliation and made the S3 mirror additive-only. Fork CI #33 is green on `16651c8`, making it the canonical custom-list code baseline.

## Do not change without an explicit new design

- Trakt remains supported and remains Showly's mature synchronization path.
- Local canonical identity remains Trakt-based for now; provider-neutral database migration is deferred.
- Floppy integration boundaries prefer TMDB/TVDB/IMDb plus season/episode coordinates, never Floppy internal item ids.
- Floppy-originated changes are now allowed only through the S4 bridge resolver/ledger. Do not add ad-hoc reverse writes that bypass its timestamp and tombstone rules.

## Debug bridge QA trigger

Debug builds now expose a shell-only QA broadcast receiver guarded by `android.permission.DUMP`. ADB shell can enqueue the normal full silent `TraktSyncWorker` using the action `${applicationId}.BRIDGE_SYNC`; production/release builds do not include this receiver. This exists to run repeatable bridge integration tests without unlocking the phone or automating foreground UI.


## Controlled live bridge validation (2026-09-05)

- Fork CI #43 (`33937077077`) on `dea10fd` passed ktlint, selected unit tests, Debug APK build, and artifact upload. The extracted APK SHA-256 is `dbbda91ff128baff26aaaba6f740de3287d7195067ce9893f2e5e22e447544ba`; it was installed successfully on CPH2573 as the debug package, with production Showly unchanged.
- Custom Lists passed a disposable live-account matrix: creation in both directions, metadata latest-wins in both directions, member add/remove/re-add in both directions, and paired list deletion in both directions. All temporary list fixtures were removed afterward.
- Watchlist initially passed Trakt -> Floppy add, Floppy -> Trakt delete, and Floppy -> Trakt re-add, but the first Trakt-side delete exposed a legacy resurrection bug: the old watchlist importer left the stale local row behind and the old exporter re-added it to Trakt before the bridge post-pass could observe the deletion.
- `f39a391` fixes this by running the bidirectional watchlist resolver as a pre-pass, just like Custom Lists. The #44 on-device re-test repeated the same Trakt-side deletion and passed: Trakt stayed deleted, Floppy Planning was removed, and the full bridge completed without failures. The disposable Manos Watchlist fixture is back to absent on both providers.
- History and Ratings still require controlled live deletion/re-add tests because their mature Trakt import/export paths may have analogous stale-local resurrection semantics and must not be assumed safe from the Watchlist result alone.
- Code audit confirmed the analogous risk: Ratings preload does not remove a local rating that disappeared remotely, and movie watched export can re-emit a stale local `MyMovie` after the last remote history event is deleted. The current working tree therefore adds Ratings and History bridge pre-passes before the legacy import/export path. For movie history, the bridge writes the exact event tombstone first and the mature exporter consults that ledger to suppress only a local event whose timestamp is not newer than the tombstone. A newer local watched timestamp is still allowed to export. The suppression policy has focused repository unit tests.
- Failure safety is also tightened in the next working tree: while Floppy bridge mode is enabled, a failed domain pre-pass blocks only that domain's mature Trakt export for the current worker run. Imports and unrelated domains continue. This prevents a temporary Floppy/network failure from allowing stale Showly local state to overwrite a remote deletion before the durable retry worker can recover. Disabling Floppy restores the normal Trakt-only export behavior.
- Ratings live QA started on the #45 build using the clean Manos fixture: a temporary Trakt rating was created and the bridge run was triggered, but the phone-side FRP/ADB proxy dropped during monitoring. The app-side WorkManager job may have completed independently; the exact Trakt/Floppy rating state must be re-read and the disposable rating removed when ADB returns before continuing the matrix.
- After ADB returned, logcat exposed the actual Ratings failure: `DefaultFloppyBridgeRemoteDataSource.setRating` attempted `PATCH /media/movie/tmdb/22293/` and received HTTP 404. Floppy returns detail HTTP 200 when provider metadata exists even if the user has zero consumptions, so detail existence is not proof that a patchable user-media row exists. The local fix parses `FloppyMediaDetail.consumptions`: zero consumptions + non-null score now POSTs an explicit `status: null` score-only row; zero consumptions + null score is a no-op; an existing consumption continues to use PATCH. Focused action-selection tests cover all three cases. The existing queued Ratings retry is intentionally retained as the live recovery fixture for the next build.
- The live failure also verified durable retry persistence on-device: `bridge_retry_state` contains a `ratings` row with `attempt_count=2` and sanitized `last_error=IOException`. This queue entry is intentionally left in place so the fixed build can prove recovery/cleanup rather than creating a new synthetic failure.
- A later live user report exposed a separate architectural gap: adding playback history or Watchlist state directly in Showly did not reliably reach either remote. Source tracing confirmed that local writes were only queued through the legacy Trakt QuickSync path and were therefore suppressible by its settings; Floppy was not a direct target. This is the reason for the schema-44 local outbox/fast-path work above. The same investigation explained multi-minute full syncs: four bridge domains ran as pre-pass, then mature Trakt import/export, then four bridge domains were scanned again, while Floppy History also had an N+1 per-identity detail loop.
- Read-only on-device DB inspection of the exact report confirmed `trakt_quick_sync_enabled=0`, `trakt_quick_remove_enabled=1`, an empty `trakt_sync_queue`, and two newly added local movie Watchlist rows (`905132`/TMDB `969681` and `993003`/TMDB `1228710`). After the user's later full manual sync both were verified present in Trakt and as Floppy Planning consumptions, proving that the broken path was the automatic local-origin path rather than permanent remote write failure. That full bridge run took about 65.3 seconds from its saved attempt/success timestamps. On this Floppy account, the old History implementation expanded 24 flat entries / 24 identities into about 25 HTTP requests per History pass; because History also ran pre- and post-pass, roughly 50 Floppy History requests could occur in one full sync. The new flat parser needs one paginated request for the same data and History no longer has a post-pass.
- The same live DB audit identified the reported playback-history miss as `House of the Dragon` S1E2 (`episode trakt=6558466`) with `last_watched_at=1788582463059`, `last_exported_at=NULL`, and no History ledger event. Its parent show is not in `shows_my_shows`, and the mature `TraktExportWatchedRunner` only enumerated watched episodes under My Shows. The next patch changes that exporter to enumerate all locally watched, not-yet-exported episodes. The audit also confirmed that the database field named `Episode.idShowTmdb` actually contains the episode TMDB id (`3846963` here), while the parent show's TMDB id is `94997`; the local Floppy fast path must resolve the parent via `episode.idShowTrakt -> shows.idTmdb`.
- Tracing the exact UI path found an additional gate before QuickSyncManager: `EpisodesSetEpisodeWatchedCase`, `EpisodesSetSeasonWatchedCase`, `ShowDetailsWatchedSeasonCase`, and Quick Progress only scheduled added episode history when the show was already in My Shows/collection. The bridge fix removes that collection gate for watched additions and delegates the decision to QuickSyncManager: with Floppy bridge enabled the shared History mutation is always queued; with Floppy disabled the legacy Trakt Quick Sync setting still controls behavior. This is the direct cause of the reported unfollowed-show episode never entering `trakt_sync_queue`.
- Schema 43 -> 44 now also recovers legacy local-only episode history safely: Trakt-imported watched episodes carry non-null `last_exported_at`, so the migration seeds only `is_watched=1 AND last_exported_at IS NULL` episodes that are not already queued into the new two-provider outbox. An offline migration rehearsal against the current device database produced the expected schema columns and seeded the two genuinely unexported local episode rows, including the reported S1E2, with both provider ACKs pending. MainTraktCase will schedule QuickSync on first post-upgrade startup because the queue is non-empty.
- Episode removals needed one more durability guard because an unfollowed episode is physically deleted from Showly immediately after being marked unwatched. Schema 44 now stores optional provider identity on each outbox row (`media_tmdb_id`, `season_number`, `episode_number`). Episode ADD/REMOVE scheduling snapshots the parent show's TMDB id and episode coordinates before local deletion; Floppy QuickSync prefers this persisted identity and only falls back to the local Episode row. The UI unwatch paths queue the REMOVE before `EpisodesManager` mutates/deletes local storage. A fresh offline 43 -> 44 rehearsal against the current device DB confirmed all 12 queue columns and seeded the reported S1E2 as parent TMDB `94997`, season `1`, episode `2`, with both provider ACKs pending.

## Startup performance audit against installed 3.70.0

The user provided a JADX/decompiled tree from the installed official 3.70.0 APK at `D:\Workspace\General\showly-3.70.0-src`. This materially changes the startup comparison:

- The official APK is an R8 `classicRelease` build; its decompiled classes carry the release R8 mapping marker.
- The official APK contains `assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm` and registers `ProfileInstallerInitializer`.
- The fork CI debug APK contains ProfileInstaller itself but **does not** contain `assets/dexopt/baseline.prof` / `baseline.profm`.
- The fork debug build is unminified and enables debug-only StrictMode/Timber behavior; the official APK is optimized/minified.
- Official 3.70.0 actually performs additional Application startup work (Firebase Remote Config and Qonversion), so the observed faster launch is not explained by a lighter application startup graph. Build/runtime optimization is the strongest verified delta.
- Do not copy the official 3.70.0 baseline profile into the fork: profile entries/metadata are tied to that APK's dex layout. Generate a fork-specific baseline profile instead.

Next startup-performance work should create a release-like independently installable QA variant and a Baseline Profile/Macrobenchmark generation path, then compare cold-start `am start -W` against official 3.70.0 once ADB is online.

## Startup performance remediation in progress

A release-like QA path and fork-owned Baseline Profile generation path are now being introduced based on the official 3.70.0 APK audit:

- `qa` inherits the release build type, keeps R8/minification enabled, runs with `BuildConfig.DEBUG=false`, and uses the existing `.debugoss` application id plus debug signing so it can replace the current fork debug install without touching production Showly or losing the fork's local data.
- A `:baselineprofile` `com.android.test` producer targets `:app` and records the startup critical path with `BaselineProfileRule`.
- Generated profile rules are configured to merge into `src/main`, so both release and release-like QA builds can consume the fork-specific profile after it is generated and committed.
- Fork CI now builds both debug and QA artifacts. A manual `Fork CI` input (`generate_baseline_profile=true`) generates the profile on an AOSP Gradle Managed Device and uploads the generated rules for review/commit; normal push CI skips this expensive job.

Do not copy the official 3.70.0 `baseline.prof`: generate and commit the fork-specific rules, then compare cold startup on the same device/data using the QA build.

Fork CI #55 on `5e6fbaf` verified the first startup-performance layer: lint, unit tests, normal Debug APK, and the new R8 `qa` APK all passed. The QA artifact is 6,333,214 bytes with SHA-256 `b9d1088a9acdf56cf4b536c6721f63ba1500c1a97b31a26a14faff4426bfd497`, versus ~17 MB for the unminified Debug APK. It already packages dependency/merged `assets/dexopt/baseline.prof` and `baseline.profm`; these are not yet the generated Showly startup rules. The next step is generating the fork-specific app profile and rebuilding QA with it.

## Fork Baseline Profile generated

Manual Fork CI run `33977099973` on `a0ce142` completed successfully. The Gradle Managed Device collected 24,013 rules into both `baseline-prof.txt` and `startup-prof.txt`; about 5,312 rules reference `com/michaldrabik/*`, including `App.onCreate`, the main activity path, Hilt, Room, and WorkManager startup code. The generated fork-specific rules are committed under `app/src/main/generated/baselineProfiles/` and will be compiled into subsequent release/QA builds together with `dexLayoutOptimization = true`.

The first QA artifact from CI #55 proved the release-like variant builds successfully and shrinks from ~17 MB Debug to 6.1 MB QA. That #55 artifact only contained dependency/merged profiles; the next QA build is the first one that will contain the generated Showly startup profile. Device A/B startup measurement remains pending because the Windows/ADB tunnel is currently unavailable.

## Release-like QA with fork profile verified

Fork CI #58 (`33977897124`) on `2e28a12` passed lint, all selected unit tests, Debug APK, and the release-like QA APK. The final QA APK is 6,543,513 bytes with SHA-256 `27f74ff8b014f6bc2437459e5003cb23239975983fa575a0d1591f5a1f340cbd`. Its packaged `assets/dexopt/baseline.prof` is 15,044 bytes (up from 3,751 bytes in the pre-app-profile #55 QA) and `baseline.profm` is 348 bytes, confirming the generated Showly startup profile is actually compiled into the APK rather than merely stored in the repository.

The next required validation is same-device startup A/B: current fork Debug -> official 3.70.0 -> profiled QA, preserving the `.debugoss` data if installation succeeds. The Windows/ADB connector is currently unavailable, so no device install or timing claim has been made yet.

## Startup regression root cause found against official 3.70.0

The prior startup audit over-weighted debug-vs-release and Baseline Profile differences. A user test of the normal `showly-debug-2e28a125...` artifact remained slow, and the artifact itself was not the release-like QA variant from the same run. CI artifacts are now named so the optimized comparison build is unambiguous: `showly-performance-qa-<sha>`.

A deeper comparison against the decompiled installed 3.70.0 source at `D:\Workspace\General\showly-3.70.0-src` found two fork-introduced eager Floppy dependencies on the real startup path. The pre-fix generated startup profile independently captured both of them:

- `QuickSyncManager.<init>(..., FloppyRemoteDataSource)` — official 3.70.0 `ie.t` has only Trakt/settings/local/transactions/WorkManager dependencies. In the fork this caused MainViewModel creation to resolve `DefaultFloppyRemoteDataSource`, which constructs the base OkHttp client, Moshi, and Floppy adapters merely to read the bridge-enabled preference.
- `MainRemoteDataSource.<init>(..., FloppyRemoteDataSource, ...)` — no code actually used `RemoteDataSource.floppy`, but the normal startup `ShowsMoviesSyncWorker` resolves the aggregate remote source and therefore eagerly constructed the Floppy network stack again.

`6a19307` introduces a lightweight `FloppyConfigStore` backed only by SharedPreferences for startup-sensitive QuickSync decisions, and makes Floppy Moshi adapters lazy. `f215b35` removes the unused Floppy member from the aggregate `RemoteDataSource` entirely. After these changes, the normal MainActivity/MainViewModel/ShowsMoviesSync startup graph has no direct Floppy dependency; Floppy networking remains injected only into actual Floppy sync workers/runners/settings. Fork CI #60 (`34005730920`) passed lint, all selected unit tests, Debug APK, and release-like QA APK for the complete pair. Baseline Profile workflow #61 (`34006010883`) then regenerated the profile on `f215b35`: the startup profile fell from 24,013 to 23,801 rules, and all 10 pre-fix `DefaultFloppyRemoteDataSource` / `FloppyRemoteDataSource` startup entries disappeared. The corrected startup profile now contains only the lightweight `FloppyConfigStore(SharedPreferences)` dependency for QuickSync enable checks; `MainRemoteDataSource` is back to Trakt/TMDB/AWS/OMDb only. The regenerated profile is committed in the next profile-refresh commit and must be used for the final performance QA build.
