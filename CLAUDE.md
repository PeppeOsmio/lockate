# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

The architecture, every component and the reasons behind each decision are in `docs/architecture.md` and the pages it links (`docs/architecture/`); `docs/roadmap.md` says what is built. Keep them current with every change.

Lockate is a zero-knowledge, end-to-end encrypted real-time location sharing app. The server stores only ciphertext - it never sees plaintext group names, member names, group keys, or GPS coordinates.

## Security invariants - never violate these

- All sensitive fields (group name, member names, coordinates) are encrypted **client-side** before leaving the device. The server must never receive or log plaintext versions of these.
- The group key is 32 random bytes generated client-side. It reaches new members only inside an invite payload encrypted with a key derived from the J-PAKE shared key.
- Every encrypted field has a fixed size, so its length tells the server nothing. The server may learn *when* members send (stillness from a still member posting every 10 minutes, circle crossings from circle events), never what: coordinates, activity, circle ids and enter/exit stay encrypted.
- The server relays invite messages as opaque blobs and stores join tokens only as SHA-256 hashes.
- Every new entity or field added to the database must follow the same zero-knowledge model.

## Architecture

Three core domain entities:
- **AnonymousGroup** - stores the encrypted name; members join only through invites
- **AGMember** - user scoped to one group; stores encrypted name, SHA-256 token hash, admin flag
- **AGMemberLocation** - encrypted GPS record; stores ciphertext + IV, never plaintext coordinates

**Encryption**: AES-256-GCM with a random 32-byte group key. Each field has its own random 12-byte IV.

**Invites**: EC-JPAKE (RFC 8236, P-256, SHA-256) from Mbed TLS 3.6 (C, `mobile_app/third_party/mbedtls` submodule) through its PSA API, wrapped by `mobile_app/jpake-native` (`jpake.c`, built into `libjpake.so` with the NDK). The KMP module `mobile_app/jpake` (package `com.peppeosmio.jpake`) is a generic, Lockate-free J-PAKE library on top of it, driven through JNA: `JPake` (low level, one round at a time), `ConfirmedKeys` (HKDF key schedule and the RFC 8236 section 5 key confirmation tags, `HMAC(k', "KC_1_U" || myId || peerId || myKeyShares || peerKeyShares)`), the ready-made flow (`JPakeServer`/`JPakeClient`/`JPakeSession`, with AES-256-GCM `seal`/`open`) and `WordPassphrase`. Everything is configurable through `JPakeConfig`, the constants are only defaults. Lockate's own protocol is `app/.../service/invite/InviteExchange.kt` (password label `lockate-invite-v2`, the session id as the HKDF context, payload info `lockate-invite-payload`) and `InviteCode`. Code = `nameplate-w1-w2-w3` (nameplate 1-9999 picks the server mailbox; the 3 EFF short-list words are the password). Flow: the inviter (J-PAKE server) posts (sid, round 1) to `POST /api/v1/anonymous-groups/{id}/invites` and gets a nameplate + single-use join token; the invitee reads them from `GET /api/v1/invites/{nameplate}` and posts (round 1, round 2) once (a second post gets 409) and gets a claim token, required for the rest of its calls (`X-Invite-Claim`); the inviter posts its round 2 + Ta (32 bytes) to `.../invites/{id}/confirm`; the invitee checks Ta and posts Tb to `/api/v1/invites/{nameplate}/confirm` (even when Ta was wrong, so the inviter learns at once); the inviter checks Tb and only then posts the payload `{groupId, groupKey, joinToken}` AES-GCM-encrypted with a key derived (HKDF) from the J-PAKE key to `.../invites/{id}/final`; the invitee decrypts it and calls `POST /api/v1/anonymous-groups/{id}/members/join`. A failed check cancels the invite, so each code gets one guess. Invites expire after 10 minutes. The server stores only opaque blobs and SHA-256 hashes of the tokens.

**Auth**: members authenticate with a 32-byte random token, stored as its SHA-256 hash (no BCrypt: a random 256-bit token can't be guessed, and every request checks it) and compared in constant time, sent as `Authorization: AGMember {memberId} {token}`.

**Real-time** (see `docs/architecture/realtime.md`): there is no WebSocket. Uploads go over REST (`AGMemberMessageService`, see `docs/architecture/sending.md`): `POST /api/v1/anonymous-groups/{id}/locations` takes `{encryptedCoordinates, encryptedActivity, circleEvents: [{cipherText, iv}, …]}` (at most 16 events, all checked before anything is relayed; the position is relayed, maybe saved and refreshes presence, then each event is published in order as a `circle.event`), limited to 120 posts per member per minute (`rate:location:{memberId}`, 429 above), and answers `{circlesVersion}` (`anonymous_group.circles_version`, bumped in the transaction of every circle upsert or delete) so a phone sharing in the background knows when to refetch the circles; `POST /api/v1/anonymous-groups/{id}/members/pause` announces at once that sharing stopped. The server's JSON frames have a `type` (`AGFrameDto`): `snapshot` (members with their last saved record, circles; it opens the SSE stream, comes with the join response and from `GET /api/v1/anonymous-groups/{id}/snapshot`), `member.joined|left|updated`, `circle.upserted|deleted`, `circle.event` (another member's circle event, with the server's timestamp, never stored), `member.presence` (`{memberId, online, lastSeen}`, see Presence) and `group.deleted`. A group is streamed live only while a screen shows it: `GET /api/v1/anonymous-groups/{id}/locations` (`AGStreams`, SSE) sends a `snapshot` event, then a `location` event per member message (`MemberLocationDto`) and a `frame` event per frame, except our own circle events and sharing changes, with comment heartbeats every 15 s from one shared scheduler; what arrives while the snapshot is built is held back in order, and the stream ends right after the `member.left` of our removal or the `group.deleted` frame. Everything else reaches a phone by push (see Push). State changes and circle events are published on the Redis channel `ag-{groupId}-events` (after the transaction commits), member messages on `ag-{groupId}-locations`; every stream subscribes to both, so it works across backend instances, and each instance delivers them in order (single-thread listener executor; a `Stream` per SSE stream). Member messages and circle events are relayed without decrypting. Every encrypted field of a member message, a circle event, a circle or a name is accepted only at its exact size (coordinates 24 bytes of ciphertext, activity 17, circle event 33, circle radius 24, any name 144) with a 12-byte IV.

**Circles**: places of a group (home, school...), created, edited and deleted by any member through `PUT|DELETE /api/v1/anonymous-groups/{id}/circles/{circleId}` (the client generates the id). Centre, radius and name are three AES-GCM fields, each with its AAD (`lockate-circle-coords-v1|groupId|circleId`, `lockate-circle-radius-v1|…`, `lockate-circle-name-v1|…`): the centre is encoded like a member's coordinates, the radius as a big-endian `Float64`, and the name (at most 40 chars) padded to 128 bytes, so every circle has the same size. Group and member names are encoded like a circle's name (at most 40 chars, padded to 128 bytes) with the AAD `lockate-group-name-v1|groupId` and `lockate-member-name-v1|groupId|memberId`, so the client generates the group and member ids as it does for circles (`groupId`/`memberId` in the create and join requests, 409 when taken). A member message is a position and the sender's activity (still/foot/bike/vehicle, from Activity Recognition), two AES-GCM fields of a fixed size so the server learns nothing from their length: the coordinates as two big-endian `Int32` microdegrees (AAD `lockate-member-coords-v1|groupId|memberId`) and the activity as one byte (AAD `lockate-member-activity-v1|groupId|memberId`). Geofencing runs on the sender's phone (`GeofenceTracker`, one per channel, tracking only ourselves): it feeds on our own fixes, ignores inaccurate ones, starts silently from the first fix and reseeds silently when a circle is added, moved or resized. Each transition is a circle event: the circle id (16 bytes) and enter/exit (1 byte) in one AES-GCM field with the AAD `lockate-circle-event-v1|groupId|memberId`, no timestamp (receivers use the server's). The events go out in the same post as the position that caused them: `GeofenceTracker` returns the transitions the server hasn't confirmed yet, a successful post confirms them, so a failed one is repeated by the next position; an entry and an exit both unconfirmed cancel out, and one unconfirmed for 5 minutes is dropped. The confirmed state is kept only in memory. While the sender is still, `PositionProvider` releases the 2 s GPS requests, sends the last good fix at once and then on every fix of a high-accuracy Fused request every 10 minutes (`STILL_FIX_INTERVAL_MILLIS`), which also wakes the phone in Doze; the location callback holds a 10 s wake lock so the post goes out. A change of activity is sent at once. When it moves again the GPS is back and the last good fix is held until an accurate one arrives (at most 20 s), so a poor first fix can't fake a departure. Notifications come only from live `circle.event` and `member.presence` frames, never from stored records, and only in a push (see Push), so a server without a relay notifies nothing; muted circles and members are kept only in Room. `CircleNotifier` shows them: a member has one notification that each new event replaces, under one summary per group (`CircleNotificationBook`), and they expire after 1 hour.

**Presence**: a member is online while it sends locations (`AGPresenceService`), decided by the server from when members send, which it already knows. Every location refreshes `ag-presence:{g}:{m}` (TTL 30 min, the grey dot on the map) and then `ag-online:{g}:{m}` (TTL 31 min, value = last seen); a nil previous `ag-online` announces `online: true`. The expiry event of `ag-presence` announces `online: false` on the instance whose Lua script (only if `ag-presence` wasn't recreated meanwhile) `GETDEL`s `ag-online`; `members/pause` does the same at once. Lost expiry events are accepted: `ag-online` expires by itself, so nothing stays stuck. The backend turns on `notify-keyspace-events` `Ex` at startup. Leaving or being removed deletes both keys silently. The app notifies presence like circle events, in the member's one notification, and doesn't store it.

**Push** (see `docs/architecture/push.md`): each member registers where its notifications go with `PUT /api/v1/anonymous-groups/{id}/members/push-registration` (`{fcmToken, registrationId, key}`, the id 16 bytes and the AES key 32, Base64), stored on `ag_member` (`push_token`, `push_registration_id`, `push_key`): one registration per member, replaced by the next. The app (`PushRegistrationService`) generates the id and the key once per group, keeps them in Room (`push_registration`, the key encrypted with the Keystore, the id unique only on the phone) and registers every group at start, each new group, and on `onNewToken`. The server pushes (`AGPushService`, `AGPushSender`) the same `AGFrameDto` the SSE stream sends, sealed per member as `{r, iv, c}` (AES-GCM with the member's key, AAD `lockate-push-v1|` + `r` in Base64), through the push relay at `lockate.push.relay-url` (`PUSH_RELAY_URL`, empty sends nothing), after the transaction commits: circle events and sharing changes to the group's other members, `member.left` to a member the admin removes, `group.deleted` to everyone but who deleted it; a 404 from the relay clears the registration. The registration answers `{pushes}` (whether a relay is set); the app only logs a server that doesn't push. The app opens a push in `AnonymousGroupService.openPush` (unknown `r` or a payload that doesn't open is dropped); a push naming a member or circle missing from Room (`namesUnknown`) first fetches `GET /api/v1/anonymous-groups/{id}/snapshot` and saves it like any snapshot, and is dropped if it still can't be described. Firebase is initialised by hand (`FirebaseSetup`) from the `lockate.firebase.{projectId,appId,apiKey,senderId}` properties (`local.properties`, a Gradle property or `LOCKATE_FIREBASE_*`), not from `google-services.json`, per flavor (see Mobile): without them the app has no FCM token and registers nothing.

**Redis** (Valkey, referred to as Redis throughout the codebase): used for the invite mailbox (`invite:{id}`, `invite-nameplate:{n}`, `invite-claim:{id}`, `invite-join:{groupId}:{tokenHash}`, 10-min TTL), rate-limit counters (`rate:*`), presence (`ag-presence:*`, `ag-online:*`) and pub/sub (`ag-{groupId}-events`, `ag-{groupId}-locations`, `__keyevent@*__:expired`).

## Coding standards

### General
- No comments unless the WHY is non-obvious (hidden constraint, workaround, subtle invariant)
- No unused code, dead branches, or backwards-compatibility shims
- No error handling for scenarios that cannot happen; trust framework guarantees
- Validate only at system boundaries (user input, external APIs)
- Before committing, run `make all` in each module you changed (`backend`, `mobile_app`, `admin_ui`, `push_relay`): it formats the code, then checks formatting, lints and tests. CI runs `make ci`, the same without formatting

### Backend (Java / Spring Boot)
- Follow existing package structure: one package per domain concern under `com.peppeosmio.lockate`
- Use `@SecuredAGMember` for any endpoint that requires member authentication
- Never log or expose plaintext sensitive fields; treat all encrypted fields as opaque blobs in service/controller layers
- TDD: write tests before implementing any feature

### Mobile (Kotlin / Android)
- All crypto operations go through `CryptoService`; never call crypto primitives directly in ViewModels or UI
- Use `_events: MutableSharedFlow<AnonymousGroupEvent>` for cross-layer communication; do not add new state channels without a clear reason
- Room stores decrypted data only; never persist ciphertext or IVs in Room tables

## Releases

- `backend-vX.Y.Z` tag → `.github/workflows/release-backend.yml` publishes `lockate-backend`, `lockate-admin-ui` and `lockate-push-relay` to Docker Hub with the same version, so they always match (`prod-docker-compose.yml` pins the backend and the admin UI with `LOCKATE_VERSION`).
- `mobile-vX.Y.Z` tag → `.github/workflows/release-mobile.yml` builds the signed APKs and attaches them to a GitHub release.
- Release workflows only build and publish; the `checks-*` workflows run on pull requests only.

## Backend

### Commands

```bash
cd backend

# Format, check formatting, lint and test (CI runs `make ci`: the same without formatting)
make all

# Run all tests
./mvnw clean test -Pdev

# Run a single test class
./mvnw test -Pdev -Dtest=InviteServiceTest

# Run dev server (requires infra running)
./mvnw spring-boot:run -Pdev

# Start dev infra (PostgreSQL + Valkey)
docker compose -f dev-docker-compose.yml up -d

# Build prod JAR
./mvnw clean package -Pprod
```

### Structure

`com.peppeosmio.lockate` splits into: `anonymous_group/` (controllers, service, entities, repos, DTOs, mappers, sse, push, presence, security, jobs), `invite/` (J-PAKE invite mailbox and join tokens), `api_key/`, `config/`, `redis/`.

The `@SecuredAGMember` annotation marks endpoints that require a valid member token. Auth is resolved in `AGMemberArgumentResolver` and passed to controllers as `AGMemberAuthentication`.

The `AnonymousGroupService` is the main orchestration point.

Member messages are always relayed, but saved only if the member's last saved record is older than the interval: the server throttles saves via a `TTLMap` - a member cannot write a new location record more often than `lockate.retention.anonymous-group.location.save-interval` (default `PT2M`).

## Push relay

`push_relay/` is a small Quarkus app compiled native (not Spring Boot: it has no state and must start in milliseconds to scale to zero) that forwards pushes from any backend to FCM with our Firebase project's credentials. `POST /v1/push` `{token, data}`, no authentication (the token is the permission), rate limits per token and per IP in memory, 404 when FCM says the token is gone. The access token comes from the metadata server on Google Cloud or from a service-account key file (`GOOGLE_APPLICATION_CREDENTIALS`), signed by hand: no Firebase Admin SDK. See `push_relay/README.md`.

```bash
cd push_relay

# Format, check formatting, lint and test (CI runs `make ci`: the same without formatting)
make all

# Dev mode
FCM_PROJECT_ID=my-project ./mvnw quarkus:dev
```

`dev-docker-compose.yml` runs it too (`push-relay`, JVM dev mode, `localhost:3120`, the compose backend's `PUSH_RELAY_URL`, staging Firebase project), with the key `terragrunt apply` in `push_relay/terraform/live/staging` writes to `push_relay/.secrets/service-account.json` (git-ignored); without the key, pushes fail with 502 and everything else works.

## Mobile

### Commands

```bash
cd mobile_app

# Format (ktfmt), check formatting, Android lint and test (CI runs `make ci`: the same without formatting)
make all

# Debug build
./gradlew assembleProdDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew testProdDebugUnitTest --tests "com.peppeosmio.lockate.ExampleUnitTest"

# Install on connected device (installStagingDebug is the staging flavor, installed beside it)
./gradlew installProdDebug
```

Two flavors, `prod` (`com.peppeosmio.lockate`) and `staging` (`.staging`, named "Lockate Staging"), install side by side with separate data, Keystore keys and FCM tokens. Each has its own Firebase app: `lockate.firebase.*` for prod, `lockate.firebase.staging.*` (`LOCKATE_FIREBASE_STAGING_*`) for staging. Releases build `prod` only.

The first clone needs `git submodule update --init --recursive` (Mbed TLS), and building needs the Android NDK and CMake 3.22.1 (Gradle installs them if the SDK licenses are accepted) plus a host `cmake` for `:jpake:jvmTest`.

### Structure

`com.peppeosmio.lockate` splits into: `ui/screens/` (6 screens), `service/` (AnonymousGroupService, InviteService, CryptoService, ConnectionService, LocationService), `data/` (Room entities, remote DTOs, mappers), `domain/`, `dao/`, `di/` (Koin modules), `android_service/` (background location foreground service).

`AnonymousGroupService` owns all networking. It holds a `_events: MutableSharedFlow<AnonymousGroupEvent>` that ViewModels collect for state changes. The foreground service (`AndroidSendLocationService`) runs only while some group is shared: `holdAllSharing` runs `holdSharing` for each shared group, which follows our location, runs our `GeofenceTracker` and posts each position with its circle events (one post at a time), refetches the snapshot and replaces the tracker's circles when the post's `circlesVersion` changes, and posts `members/pause` when it stops; with no group left the service stops itself, and `LockateApp` starts it again when a group starts being shared. `holdGroupStream` is the group's SSE stream, held only by the screens showing it (map, circles): frames are applied to Room and then exposed on `groupFrames`, positions are saved to Room before the map gets them. Every snapshot (stream, join, `GET …/snapshot`) is decrypted and saved by the same code, under a per-group lock. `PayloadCodec` encrypts and decrypts member messages, circle events and circles. Reconnection uses a backoff (max 60s).

`CryptoService` uses the `whyoleg/cryptography` Kotlin library for AES-GCM and group key generation. `InviteExchange` is Lockate's invite protocol on the generic `:jpake` module and `InviteService` drives it over HTTP (`:jpake` is Kotlin Multiplatform; the native part is `:jpake-native`, and an iOS client would call the same C code through cinterop). Both are singletons via Koin.

The Room database stores decrypted data locally (group name, member name, coordinates) for UI use. The encryption key is stored encrypted in the `anonymous_group` table (`keyCipher`, `keyIv`) using Android Keystore via `KeystoreService`.