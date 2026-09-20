# Unit testing plan

## Context

`mobile_app` currently has zero real unit tests — `app/src/test/java/.../ExampleUnitTest.kt` and `app/src/androidTest/java/.../ExampleInstrumentedTest.kt` are both untouched Android Studio placeholders, and `testImplementation(libs.junit)` (plain JUnit4) is the only test dependency configured. The repo-root `CLAUDE.md` mandates TDD for the backend but the mobile module has no equivalent tooling yet, so any attempt at TDD here is currently blocked on setup.

This plan establishes the testing stack and a prioritized, incremental order of coverage, starting from the highest-value/lowest-friction code (pure logic, no Android framework deps) and working toward the more entangled services. It intentionally does not attempt Compose UI testing (no harness exists, would need `androidTest` + semantics matchers — a separate initiative) or full coverage of Android-framework-bound code (Keystore, LocationManager, foreground services) — those belong in instrumented tests, not this plan.

## Testing stack to add

None of these exist in the project yet. Add to `gradle/libs.versions.toml` and wire into `app/build.gradle.kts` under `testImplementation`:

- **kotlinx-coroutines-test** — `runTest`, `TestDispatcher`, needed for any `suspend fun` or `Flow`/`StateFlow`/`Channel` code (nearly everything in `service/` and `ui/screens/*/*ViewModel.kt`).
- **MockK** (`mockk` + its coroutines support is built in) — idiomatic Kotlin mocking; handles `final` classes without extra config, unlike Mockito. Used to fake constructor-injected dependencies (`ConnectionService`, `AnonymousGroupService`, `HttpClient`, DAOs) in ViewModel/service tests.
- **Turbine** — `.test { }` for asserting emissions from `StateFlow`/`SharedFlow`/`Channel`-backed properties (`state`, `snackbarEvents`, `navigateBackEvents`, `events`), which every ViewModel and several services expose.
- Keep plain **JUnit4** (already present) — matches `androidx.test.runner.AndroidJUnitRunner` already configured, no reason to switch to JUnit5 for a single module.
- **No Robolectric for now.** Defer anything requiring real Android framework classes (`android.security.keystore.*`, `LocationManager`, sensors) to instrumented tests — out of scope here.

## Test source layout

Mirror `app/src/main/java/com/peppeosmio/lockate/...` 1:1 under `app/src/test/java/com/peppeosmio/lockate/...`, one `XyzTest.kt` per `Xyz.kt`. This matches the convention already used in the backend module's recently-added test suite. Delete `ExampleUnitTest.kt` once the first real test lands; leave `ExampleInstrumentedTest.kt` alone (androidTest is untouched by this plan).

## Priority tiers

### Tier 1 — pure logic, no mocking needed (do first)

- **`utils/DoubleBytesUtils.kt`** — `doubleToByteArray`/`byteArrayToDouble` round-trip for representative values (0, negative, `Double.MIN_VALUE`/`MAX_VALUE`, NaN/Infinity), both endiannesses, and the `IllegalArgumentException` for a non-8-byte input.
- **`utils/DateTimeUtils.kt`** — `utcToCurrentTimeZone` conversion correctness against a fixed `TimeZone` (don't depend on the CI machine's local zone — pass a known zone or accept the currently-hardcoded `TimeZone.currentSystemDefault()` and assert via computed expectation, not a hardcoded string).
- **`data/anonymous_group/mappers/*.kt`** (6 mapper objects: `ConnectionMapper`, `AnonymousGroupMapper`, `AGMemberMapper`, `AGLocationUpdateMapper`, `LocationRecordMapper`, `EncryptedDataMapper`) — `toEntity`/`toDomain`/DTO round trips, explicit null-field handling (e.g. `ConnectionMapper`'s always-null `username`/`authToken`, `apiKey` nullability).
- **`service/crypto/CryptoService.kt`** — `createKey` is deterministic for the same `(password, salt)`; `encrypt`→`decrypt` round-trips to the original plaintext; decrypting with the wrong key or a tampered ciphertext/IV throws `CryptoException`. This is a security-critical path (see repo-root `CLAUDE.md`'s zero-knowledge invariants) and should get the most thorough coverage in Tier 1.
- **`service/srp/SrpClientService.kt`** — `generateVerifier` is deterministic for the same `(identifier, password, salt)`; `getA`/`getM1` produce non-null `BigInt`s of the expected size. For a real end-to-end assertion, pair `SrpClientService` against BouncyCastle's `SRP6Server` directly in the test (no server-side production code needed, just the library's own server class) to prove a full client/server handshake succeeds — mirrors what the backend's `StatelessSRP6Server` tests already validate from the other side.
- **`utils/ErrorHandler.kt`** — `runAndHandleException`'s exception→`ErrorInfo` mapping (one case per branch: `RemoteAGNotFoundException`, `LocalAGNotFoundException`, `ConnectException`, `SerializationException`, `InvalidApiKeyException`, `AGMemberUnauthorizedException`, `APIException`, generic fallback) and the `customHandler` short-circuit path — all just pass a `callback` lambda that throws, no HTTP mocking needed. `handleUnauthorized`/`handleGeneric` need an `HttpResponse`; use Ktor's `MockEngine`/`MockRequestHandleScope` (already a transitive dependency via `ktor-client-*`) rather than mocking `HttpResponse` directly with MockK, since Ktor's response type is awkward to fake by hand.

### Tier 2 — ViewModel state/business logic (mock the service layer)

Every ViewModel in `ui/screens/*/` is constructor-injected with plain service classes (Koin `viewModel<T> { T(dep = get<Dep>()) }`), so they're testable in isolation: instantiate directly with MockK-mocked services, drive with `runTest`, assert on `state.value` and Turbine-collected events. Suggested order, matching recency/risk:

1. **`ConnectionSettingsViewModel`** — blank-name check fires *before* blank-url (per the naming/connections plan just implemented), blank-apiKey-when-required, `requireApiKey` auto-detection via `checkRequireApiKey`, successful create vs. update paths (verify the single `Connection(...)` construction site gets all fields including `name`).
2. **`ManageConnectionsViewModel`** — `deleteConnection()`'s cascade (`leaveAllAG` success path vs. `deleteAllAG` fallback on failure), `navigateBackEvents` firing only when the post-delete list is empty, snackbar on failure, `selectForDelete`/`openDeleteConfirmDialog` state toggles.
3. **`HomePageViewModel`** — the new `connectionService.events` collector: reselection when the deleted connection was active vs. untouched when it wasn't, `saveSelectedConnectionSettingsId` called only when a new id exists, the idempotency guard (event for an id not in `state.connections` is a no-op), the empty-list → onboarding redirect, and `disconnect()`'s simplified body (no longer duplicating reselect logic).
4. **`AnonymousGroupsViewModel`** — `collectAGEvents`'s branch per `AnonymousGroupEvent` variant (`NewAnonymousGroupEvent` prepends, `DeleteAnonymousGroupEvent` filters out, `RemoteAGDoesntExistEvent`/`RemoteAGExistsEvent`/`Removed...`/`Readded...` toggle the matching AG's flags) — already nontrivial branching, good payoff.
5. **`AGDetailsViewModel`** (477 lines, largest ViewModel) — defer to a follow-up pass given its size; don't block the rest of Tier 2 on it.
6. **`LoadingViewModel`, `CreateAnonymousGroupViewModel`, `JoinAnonymousGroupViewModel`** — smaller, round these out last.

### Tier 3 — orchestration services (mock DAO/HttpClient/lower-level services)

- **`ConnectionService`** — CRUD + `listConnectionSettings` ordering, `getSelectedConnectionSettings`'s fallback to `getFirstConnection`, the new `ConnectionEvent.ConnectionDeletedEvent` emission on delete. Note: `saveSelectedConnectionSettingsId`/`getSelectedConnectionSettings` read/write a `Context.dataStore` — either fake the DataStore (Jetpack DataStore has an in-memory test factory) or scope those two methods out of the first pass and cover the rest.
- **`AnonymousGroupService`** (1,031 lines — by far the largest and most complex class in the module) — highest value, highest cost. Don't attempt full-file coverage in one pass: start with self-contained methods that don't chain multiple network calls (e.g. `listLocalAnonymousGroups`, `deleteLocalAnonymousGroup`, `leaveAllAG`/`deleteAllAG`'s loop-and-delegate behavior), then expand outward. Treat this as its own multi-session effort once Tiers 1–2 are done.

### Explicitly out of scope

- Compose screens (`ui/screens/*/*Screen.kt`) — no Compose UI test harness configured; would need `androidTestImplementation(libs.androidx.ui.test.junit4)` (already present but unused) wired up with actual test classes, semantics tags, etc. Separate initiative.
- `android_service/AndroidSendLocationService.kt`, `android_service/BootReceiver.kt` — foreground service / broadcast receiver, needs instrumented tests.
- `platform_service/KeyStoreService.kt`, `platform_service/LocationService.kt`, `platform_service/DeviceOrientationService.kt`, `service/PermissionsService.kt` — hard dependencies on `android.security.keystore.*`, `LocationManager`, sensors, `ContextCompat` permission checks. Candidates for instrumented tests later, not unit tests now.

## Conventions for the tests themselves

- Prefer state-based assertions (`assertEquals(expected, viewModel.state.value)`) over MockK `verify {}` interaction checks, matching this codebase's preference for straightforward, non-over-engineered code — only reach for `verify` when the side effect itself is the thing under test (e.g. "did `saveSelectedConnectionSettingsId` get called with the new id").
- Use MockK's `relaxed = true` mocks for dependencies where only a couple of methods matter per test, to avoid stubbing every unrelated method.
- Wrap every test body touching a `suspend fun` in `runTest { }`. Most services/ViewModels call `withContext(Dispatchers.IO)` or `withContext(Dispatchers.Default)` directly rather than taking an injected `CoroutineDispatcher` — `runTest`'s dispatcher substitution handles this fine for now. If a specific test proves flaky because of this, that's a signal to introduce a constructor-injected dispatcher seam in that one class — don't pre-emptively refactor every service for it.
- One assertion focus per test; name tests `` `method - scenario - expected outcome` `` (backtick names) for readability, matching common Kotlin test style.

## Verification

- `./gradlew test` — full suite passes.
- `./gradlew testDebugUnitTest --tests "com.peppeosmio.lockate.<package>.<Class>Test"` — run a single class while iterating.
- No production code changes should be required to land Tier 1 or most of Tier 2 — if a test reveals a class needs a seam (e.g. `ConnectionService`'s DataStore access) to be testable, treat that as a small, explicitly-called-out refactor decided at the time, not something to do upfront across the board.
