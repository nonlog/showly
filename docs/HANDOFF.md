# Development Handoff

Last updated: 2026-09-05

## Active line

- Repository: `nonlog/showly`
- Active branch: `feat/runtime-credentials-free-features`
- Upstream baseline: `trakt/showly@ec897b65b1b55c18ce24a755f83f894f422e559a`
- Latest fully verified code head: `7adf0f17aedd8bb684decab2bb7ccbb0f8a16979` (`fix: gate legacy exports on bridge prepass`).
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
- Device install baseline: CI #45 debug APK is installed. CI #44 already passed the controlled Watchlist regression re-test; production Showly remains untouched.

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

1. Resume the controlled Ratings matrix when ADB returns: first inspect whether the in-flight Trakt rating reached Floppy, then finish delete/re-add tests in both directions and clean the fixture.
2. Perform controlled History/rewatch deletion/re-add tests in both directions.
3. Verify the bridge pre-pass failure gate so a temporary Floppy/network failure cannot let stale Showly state overwrite remote deletions.
4. Validate that a deliberately recoverable bridge-domain failure is queued in `bridge_retry_state`, retried by WorkManager, and cleared after convergence; only then decide whether item-level pending/conflict diagnostics are necessary.

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
