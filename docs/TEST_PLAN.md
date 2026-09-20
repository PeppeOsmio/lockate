# Unit Test Plan: Lockate Backend + Mobile App

## Context

Lockate has almost no real unit test coverage today. The backend has exactly one meaningful test (`backend/src/test/java/com/peppeosmio/lockate/srp/SrpServiceTest.java`) plus a trivial empty Spring context-load smoke test. The mobile app has zero real coverage — only the default Android Studio placeholder tests exist, and the project has no mocking library, no coroutines-test, no Turbine wired up at all (only plain JUnit4).

The goal is to unit test every critical part of the app, following the style already established by `SrpServiceTest`: mock only the infrastructure boundary (e.g. `RedisService`), and run real domain/crypto logic through the code under test (real BouncyCastle SRP6, real AssertJ-style fluent assertions, Mockito verify/ArgumentCaptor on mocked collaborators). This plan enumerates, for both subprojects, which classes get new test files, what's mocked vs. real, and what behaviors each file must cover — prioritized so the cheapest, highest-value tests (pure logic, no mocking) land first, and framework-bound code that needs Testcontainers/Room/AndroidKeyStore/instrumented tests is explicitly deferred as out of scope for this pass.

During exploration, four real correctness gaps surfaced in existing code that a straight "test what's there" pass would either bake in as correct or have to awkwardly document as broken. Decision: for all four, **fix the underlying code first, then write the test against the corrected behavior**:

1. **`AnonymousGroupService.deleteMember`** (`backend/src/main/java/com/peppeosmio/lockate/anonymous_group/service/AnonymousGroupService.java:250-262`) — looks up the member via `agMemberRepository.findById(anonymousGroupId)`, passing the *group* ID where a *member* ID is expected. This means the lookup will throw `UnauthorizedException` immediately in virtually all real cases (a member's own UUID coincidentally equaling its group's UUID essentially never happens), even though the actual delete call two lines later correctly uses `agMemberAuthentication.getId()`. **Fix**: change the lookup to `agMemberRepository.findById(agMemberAuthentication.getId())`.
2. **`AGMemberAuthenticator`** (`backend/src/main/java/com/peppeosmio/lockate/anonymous_group/security/AGMemberAuthenticator.java:33-34`) — a malformed UUID throws uncaught `IllegalArgumentException`, and a header with no token segment throws uncaught `ArrayIndexOutOfBoundsException`, both of which would surface as raw 500s on a security-sensitive auth path. **Fix**: wrap parsing so malformed input throws `UnauthorizedException` instead.
3. **Mobile WebSocket reconnect backoff** (`mobile_app/app/src/main/java/com/peppeosmio/lockate/service/anonymous_group/AnonymousGroupService.kt:669-814`) — root `mobile_app/CLAUDE.md` documents "exponential backoff (max 50s)" but the code is linear (`waitSeconds = 5 * min(retries, 10)` → 5,10,...,50s), and it's embedded inside a large method mixing WS I/O, crypto, and DB calls. **Fix**: extract a small pure function `nextBackoffDelay(retries: Int): Duration` (keeping the linear formula — behavior is unchanged, only testability improves), and correct the CLAUDE.md wording from "exponential" to "linear".
4. **`Connection.getWebSocketUrl()`** (`mobile_app/app/src/main/java/com/peppeosmio/lockate/domain/Connection.kt`) — matches via `startsWith("http")`/`startsWith("https")` without requiring `://`, so a URL like `"httpfoo.com"` would incorrectly get mangled by `.replace(...)` instead of falling through to the `ws://$url` default. **Fix**: tighten to `startsWith("https://")` / `startsWith("http://")`.

These four fixes are small, low-risk, and scoped tightly to what's described above — no other refactoring.

## Approach

Mirror `SrpServiceTest`'s style throughout: one test class per production class, package-mirrored under each project's test source root. Backend uses JUnit5 + Mockito (`@ExtendWith(MockitoExtension.class)`) + AssertJ (all already available via `spring-boot-starter-test`, confirmed — no backend dependency changes needed). Mobile needs new test dependencies added first (Phase 0 below).

General rule for what to mock: infrastructure/framework boundaries only (DB repositories, Redis, HTTP client, Android Keystore, DataStore/Context). Real logic — including real crypto (BouncyCastle SRP6, whyoleg cryptography AES-GCM/PBKDF2, BCrypt) — runs through the code under test wherever feasible, exactly like `SrpServiceTest` pairs a mocked `RedisService` with a real BouncyCastle client.

Repository classes with native `@Query`/`@Modifying` methods, `RedisService` itself (builds its own `StringRedisTemplate` internally, not constructor-mockable), Room `@Transaction` DAO methods, and `KeyStoreService`'s real AndroidKeyStore implementation are **out of scope** for this unit-test pass — they need integration tests (`@DataJpaTest`/Testcontainers) or instrumented Android tests instead, per CLAUDE.md's own guidance that infra-level things get real-DB/Redis integration tests, not mocks.

---

## Backend (no new dependencies needed)

### Phase 1 — Pure/cheap, minimal-to-no mocking

| Test file | Covers |
|---|---|
| `backend/src/test/java/com/peppeosmio/lockate/utils/TTLMapTest.java` | Real `TTLMap`: put/get/remove; re-put resets TTL without a stale expiry thread removing the new value; expiry actually removes entries after TTL (short durations + polling, no new library needed — a small retry-loop helper is fine). |
| `backend/src/test/java/com/peppeosmio/lockate/anonymous_group/mapper/AGMemberMapperTest.java`, `AnonymousGroupMapperTest.java`, `LocationRecordMapperTest.java` | Field/base64 round-trip mapping; `AGMemberMapper`'s null-last-location → `Optional.empty()` branch. |
| Entity factory tests: `AnonymousGroupEntityTest.java`, `AGMemberEntityTest.java`, `AGMemberLocationEntityTest.java` (mirror `backend/src/main/java/com/peppeosmio/lockate/anonymous_group/entity/` package) | `fromBase64Fields` valid/invalid-base64 cases; `AGMemberEntity`'s `BCrypt.hashpw`/`checkpw` round trip + wrong-token check + UTC `createdAt`. |
| `backend/src/test/java/com/peppeosmio/lockate/common/dto/EncryptedDataDtoTest.java` | `toEncryptedString()`/`fromEncryptedString()` round trip; invalid-base64 input handling. |
| `backend/src/test/java/com/peppeosmio/lockate/config/JacksonConfigTest.java` | Real `ObjectMapper` built from the `@Bean` method directly (no Spring context needed): `LocalDateTime` serializes as `yyyy-MM-dd'T'HH:mm:ss.SSSSSS` UTC; `Optional` (de)serializes correctly via `Jdk8Module`. |
| `backend/src/test/java/com/peppeosmio/lockate/anonymous_group/security/AGMemberAuthenticatorTest.java` | **After the fix above**: valid header → delegates correctly; null header/wrong prefix → `UnauthorizedException`; malformed UUID → `UnauthorizedException` (not `IllegalArgumentException`); missing token segment → `UnauthorizedException` (not `ArrayIndexOutOfBoundsException`). |

### Phase 2 — Service-level, mock infra deps

| Test file | Covers |
|---|---|
| `backend/src/test/java/com/peppeosmio/lockate/anonymous_group/service/AnonymousGroupServiceTest.java` (largest file, do first in this phase) | Mocks: `AGLocationConfigurationProperties`, `AnonymousGroupRepository`, `AGMemberRepository`, `AGLocationRepository`, `RedisService`, `SrpService`, `ObjectMapper`, `AnonymousGroupMapper`, `AGMemberMapper`. Cases: `authenticateMember` (correct/wrong token, group/member mismatch); `createAnonymousGroup` (random 32-byte token returned raw but stored hashed); `getMemberSrpInfo`; `startMemberSrpAuth`/`verifyMemberSrpAuth` (`CryptoException`→`UnauthorizedException`, `IllegalArgumentException`→`Base64Exception`, success creates non-admin member); **`deleteMember` after the fix** — correct member can delete themselves, mismatched group throws; `saveLocation` throttle boundary (`Duration` just under/at/over `saveInterval`; always publishes to Redis regardless; no-timestamp fallback queries DB); `streamLocations` (capture Redis subscribe consumer via `ArgumentCaptor`, assert author's own updates filtered out, unsubscribe `Runnable` works); `deleteAnonymousGroup` (admin vs. non-admin → `AGMemberNotAdminException`). |
| `backend/src/test/java/com/peppeosmio/lockate/anonymous_group/security/AGMemberAuthInterceptorTest.java` | Real `MockHttpServletRequest`/`MockHttpServletResponse` (from `spring-test`, already on classpath), mock `AGMemberAuthenticator`. Non-`HandlerMethod` passthrough; unsecured method passthrough; missing path variable/header → error response; `AGNotFoundException`/`UnauthorizedException` → correct JSON body + status. |
| `backend/src/test/java/com/peppeosmio/lockate/api_key/ApiKeyServiceTest.java` | Mock `ApiKeyRepository`. Lookup-by-UUID success/not-found; `lastValidated` timestamp updated on successful validation. |
| `backend/src/test/java/com/peppeosmio/lockate/api_key/ApiKeyAuthFilterTest.java` | Mock `ApiKeyService` + `FilterChain`, real `MockHttpServletRequest/Response`. Missing header; malformed UUID (caught as `IllegalArgumentException` — confirm existing behavior, don't change); whitelisted paths bypass. |
| `backend/src/test/java/com/peppeosmio/lockate/common/GlobalExceptionHandlerTest.java`, `.../anonymous_group/AGExceptionHandlerTest.java`, `.../api_key/ApiKeyExceptionHandlerTest.java` | Call handler methods directly with constructed exceptions + a request with a controlled `Accept` header. Generic `Exception` → SSE-empty-body vs JSON-500 branching; each specific exception → its mapped HTTP status. |
| `backend/src/test/java/com/peppeosmio/lockate/anonymous_group/jobs/LocationRetentionJobTest.java` | Mock `AGLocationConfigurationProperties`, `AGLocationRepository`. Cutoff = `Instant.now() - retentionDuration` passed to `deleteOldLocations` (small delta tolerance). |

### Phase 3 — WebSocket layer

| Test file | Covers |
|---|---|
| `backend/src/test/java/com/peppeosmio/lockate/anonymous_group/websocket/AGSendLocationHandshakeInterceptorTest.java` | Mock `AGMemberAuthenticator`. Path-segment parsing (index 4) with well-formed/malformed/short paths; header auth failures → 400/404/401/500 mapping. |
| `backend/src/test/java/com/peppeosmio/lockate/anonymous_group/websocket/AGSendLocationWSHandlerTest.java` | Mock `AnonymousGroupService`, real/mock `WebSocketSession`. If the internal `TTLMap` field has no test-visible accessor, add a small package-private getter (flag as a minor production change) rather than reaching in via reflection. Throttled save-on-message (rapid repeats within TTL only save once); close cleans up. |

### Backend — explicitly out of scope for this pass
`RedisService` (not constructor-mockable), all native `@Query`/`@Modifying` repository methods (`AGLocationRepository.deleteOldLocations`/`findLastLocationOfMembers`, `AnonymousGroupRepository.deleteAnonymousGroup`, `AGMemberRepository.findMembersWithLastLocation`) — need `@DataJpaTest`/Testcontainers, `CliRunner` (calls `System.exit`), trivial Spring wiring classes (`SecurityConfig`, `WebConfig`, `WebSocketConfig`, `JobSchedulingConfig`, `RequestLoggingConfig`), and controller pass-through logic beyond what's covered by the exception-handler tests above.

---

## Mobile App

### Phase 0 — Test infrastructure setup (prerequisite for everything below)

Add to `mobile_app/gradle/libs.versions.toml`:
```toml
mockk = "1.13.13"
kotlinxCoroutinesTest = "1.9.0"
turbine = "1.2.0"
```
and corresponding entries under `[libraries]` for `mockk`, `kotlinx-coroutines-test`, `turbine`, and `ktor-client-mock` (same `ktorIo` version already pinned in the catalog, for the `MockEngine`).

Add to `mobile_app/app/build.gradle.kts`:
```kotlin
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)
testImplementation(libs.ktor.client.mock)
```
Keep the existing `testImplementation(libs.junit)` (JUnit4) — MockK/Turbine/coroutines-test all work fine on it; no need to migrate to JUnit5.

**Defer Robolectric.** It would only unlock a handful of `Context`-dependent classes (`ConnectionService`'s DataStore extension, parts of `LocationService`), and those still can't be fully exercised because `KeyStoreService` needs a real AndroidKeyStore provider Robolectric doesn't supply. Not worth the setup/CI cost for this pass.

### Phase 1 — Pure logic, no mocking

| Test file | Covers |
|---|---|
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/utils/DoubleBytesUtilsTest.kt` | `doubleToByteArray`/`byteArrayToDouble` round trip (zero, negative, large/small values); `IllegalArgumentException` on non-8-byte input. |
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/domain/CoordinatesTest.kt` | `toByteArray`/`fromByteArray` round trip, byte-order correctness, `toMapLibreComposePosition()`. |
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/domain/ConnectionTest.kt` | **After the `getWebSocketUrl()` fix**: `https://...`→`wss://...`, `http://...`→`ws://...`, bare host→`ws://host`, and confirm `"httpfoo.com"`-style inputs now correctly fall through to the default instead of being mangled. |
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/service/crypto/CryptoServiceTest.kt` | Real `CryptographyProvider.Default` (no mocking needed, works off-device). Round-trip encrypt/decrypt; wrong key/garbled ciphertext → `CryptoException`; deterministic key derivation for fixed salt+password. |
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/service/srp/SrpClientServiceTest.kt` | No mocking. Mirror `SrpServiceTest`'s pairing approach — real client-side `SrpClientService` against a real BouncyCastle `SRP6Server` for a true end-to-end handshake; wrong password fails verification. |
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/data/anonymous_group/mappers/AGMemberMapperTest.kt`, `EncryptedDataMapperTest.kt`, `LocationRecordMapperTest.kt` | Real `CryptoService` as the only collaborator. Epoch-millis↔`LocalDateTime` UTC round trip, nullable last-location reconstruction, `dtoToDomain` decrypt; base64 round trip + malformed-base64 error; coordinate encrypt/decrypt round trip + `InvalidByteCoordinatesException` on malformed bytes. |
| `equals`/`hashCode` contract tests for `AnonymousGroupEntity` (Room entity), domain `AnonymousGroup`, domain `EncryptedData` (all have hand-written implementations due to `ByteArray` fields) | Two instances differing only in one `ByteArray` field are unequal; equal-content-different-array-instance are equal; `hashCode` consistency. |
| Pure-function VM tests: `CreateAnonymousGroupViewModel.canConfirm()`, `JoinAnonymousGroupViewModel.canConfirm()`, `AGDetailsViewModel.handleMembersWithSameName` | Field-validation truth tables; name-dedup/suffix logic (`"Bob","Bob"`→`"Bob (1)"`, `"Bob (2)"`; already-unique names untouched). May need a thin MockK setup just to construct the VM if these aren't extractable as standalone functions — check at implementation time. |

### Phase 2 — Service/ViewModel logic with mocked infra (needs Phase 0)

| Test file | Covers |
|---|---|
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/service/anonymous_group/AnonymousGroupServiceTest.kt` (largest mobile file, do first in this phase) | Mocks: `AnonymousGroupDao` (fake in-memory impl or MockK), `ConnectionService`, `HttpClient` (via `ktor-client-mock`'s `MockEngine`), `LocationService`, `KeyStoreService` (introduce a simple in-memory fake implementing its interface, reusable across mobile tests). Real: `CryptoService`, `SrpClientService`. Cases: create-group/auth orchestration error-status branching (401/403/404); `AnonymousGroupEvent` emission on the `_events` `MutableSharedFlow` asserted via Turbine; `sendLocation(onStatusUpdate)` job-lifecycle state machine. **After the backoff extraction**: a dedicated small test for the extracted `nextBackoffDelay(retries: Int): Duration` pure function (5s, 10s, ... capped at 50s at retries=10, and beyond). |
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/service/ConnectionServiceTest.kt` | Mocks: `HttpClient` (MockEngine), `ConnectionDao` (fake/MockK), `KeyStoreService` (fake). Skip Context/DataStore-backed parts. Cover `getSelectedConnectionSettings`'s fallback-to-first-connection branch; `saveConnectionSettings`'s guard clause (`id != null` → `IllegalArgumentException`). |
| `mobile_app/app/src/test/java/com/peppeosmio/lockate/platform_service/LocationServiceTest.kt` | Mocks: `FusedLocationProviderClient` (MockK), `PermissionsService`. `activeCollectors` ref-counting: first subscriber starts GPS updates, later subscribers don't restart, last unsubscribe stops, mid-stream subscribers don't stop it early. |
| ViewModel tests needing MockK + coroutines-test + Turbine: `AnonymousGroupsViewModelTest.kt`, `HomePageViewModelTest.kt`, `ConnectionSettingsViewModelTest.kt`, `AGDetailsViewModelTest.kt` (largest, do last) | `AnonymousGroupsViewModel.collectAGEvents` state-reducer (insert-at-front on new AG, filter-out on delete, per-id field flips); `HomePageViewModel` init-block navigation branches + `disconnect()`'s `leaveAllAG`→`deleteAllAG` fallback + `onConnectionSelected` no-op guard; `ConnectionSettingsViewModel.onConnectClicked` validation + `checkRequireApiKey` branching; `AGDetailsViewModel`'s `getRemoteMembers` merge-by-timestamp, `streamLocations` reducer, `AGLocationSentEvent` handling, `onTapLocate`/`onTapFollow` transitions. Drive suspend functions with `runTest`, assert `StateFlow`/`SharedFlow` emissions with Turbine. |

### Mobile — explicitly out of scope for this pass
`KeyStoreService`'s real AndroidKeyStore implementation (needs a real device/emulator — use the fake from Phase 2 for consumers, leave the real impl to `androidTest`); Room DAO `@Transaction` methods (`AnonymousGroupDao.createAGWithMembers`/`setAGMembers`, need in-memory Room `androidTest`); `AndroidSendLocationService`/`BootReceiver` (Android framework classes — as a small prerequisite, extract the notification-text-selection `when` mapping into a standalone function and unit test only that, leave the rest to `androidTest`); the ~19 trivial `@Serializable` remote DTOs; Koin DI modules (`AppModule.kt`, `ViewModelModule.kt`).

---

## Recommended cross-project ordering

1. Backend Phase 1 (pure/cheap — no new dependencies)
2. Mobile Phase 0 (add MockK/coroutines-test/Turbine/ktor-client-mock)
3. Mobile Phase 1 (pure logic — same tier as backend Phase 1)
4. Backend Phase 2, starting with `AnonymousGroupServiceTest` (highest-value single file in the codebase)
5. Mobile Phase 2, starting with `AnonymousGroupServiceTest.kt` (mirrors backend Phase 2 in shape)
6. Backend Phase 3 (WebSocket layer — lowest priority, most setup friction)
7. The two "out of scope" lists become a separate follow-up effort (Testcontainers/`@DataJpaTest` for backend infra; Robolectric/`androidTest` + the small pure-function extractions noted above for mobile)

## Verification

- Backend: `cd backend && ./mvnw test -Pdev -Dtest=<NewTestClass>` per new file, then `./mvnw clean test -Pdev` for the full suite once each phase lands.
- Mobile: `cd mobile_app && ./gradlew testDebug --tests "com.peppeosmio.lockate.<NewTestClass>"` per new file, then `./gradlew test` for the full suite once each phase lands.
- For the 4 pre-test fixes: confirm behavior via the specific new/updated test asserting the corrected outcome (e.g. `AnonymousGroupServiceTest`'s `deleteMember` case should show a legitimate member successfully deleting themselves, which was impossible before the fix).
- No manual/browser verification needed — this is backend/mobile unit-test work with no UI surface.
