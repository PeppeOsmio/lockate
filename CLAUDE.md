# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Lockate is a zero-knowledge, end-to-end encrypted real-time location sharing app. The server stores only ciphertext - it never sees plaintext group names, member names, group keys, or GPS coordinates.

## Security invariants - never violate these

- All sensitive fields (group name, member names, coordinates) are encrypted **client-side** before leaving the device. The server must never receive or log plaintext versions of these.
- The group key is 32 random bytes generated client-side. It reaches new members only inside an invite payload encrypted with a key derived from the J-PAKE shared key.
- Members send location updates even when stationary, so the server cannot infer stillness from silence.
- The server relays invite messages as opaque blobs and stores join tokens only as SHA-256 hashes.
- Every new entity or field added to the database must follow the same zero-knowledge model.

## Architecture

Three core domain entities:
- **AnonymousGroup** - stores the encrypted name; members join only through invites
- **AGMember** - user scoped to one group; stores encrypted name, BCrypt token hash, admin flag
- **AGMemberLocation** - encrypted GPS record; stores ciphertext + IV, never plaintext coordinates

**Encryption**: AES-256-GCM with a random 32-byte group key. Each field has its own random 12-byte IV.

**Invites**: EC-JPAKE (RFC 8236, P-256, SHA-256) from Mbed TLS 3.6 (C, `mobile_app/third_party/mbedtls` submodule) through its PSA API, wrapped by `mobile_app/jpake-native` (`jpake.c`, built into `libjpake.so` with the NDK). The KMP module `mobile_app/jpake` (package `com.peppeosmio.jpake`) is a generic, Lockate-free J-PAKE library on top of it, driven through JNA: `JPake` (low level, one round at a time), `ConfirmedKeys` (HKDF key schedule and the RFC 8236 section 5 key confirmation tags, `HMAC(k', "KC_1_U" || myId || peerId || myKeyShares || peerKeyShares)`), the ready-made flow (`JPakeServer`/`JPakeClient`/`JPakeSession`, with AES-256-GCM `seal`/`open`) and `WordPassphrase`. Everything is configurable through `JPakeConfig`, the constants are only defaults. Lockate's own protocol is `app/.../service/invite/InviteExchange.kt` (password label `lockate-invite-v2`, the session id as the HKDF context, payload info `lockate-invite-payload`) and `InviteCode`. Code = `nameplate-w1-w2-w3` (nameplate 1-9999 picks the server mailbox; the 3 EFF short-list words are the password). Flow: the inviter (J-PAKE server) posts (sid, round 1) to `POST /api/anonymous-groups/{id}/invites` and gets a nameplate + single-use join token; the invitee reads them from `GET /api/invites/{nameplate}` and posts (round 1, round 2) once (a second post gets 409) and gets a claim token, required for the rest of its calls (`X-Invite-Claim`); the inviter posts its round 2 + Ta (32 bytes) to `.../invites/{id}/confirm`; the invitee checks Ta and posts Tb to `/api/invites/{nameplate}/confirm` (even when Ta was wrong, so the inviter learns at once); the inviter checks Tb and only then posts the payload `{groupId, groupKey, joinToken}` AES-GCM-encrypted with a key derived (HKDF) from the J-PAKE key to `.../invites/{id}/final`; the invitee decrypts it and calls `POST /api/anonymous-groups/{id}/members/join`. A failed check cancels the invite, so each code gets one guess. Invites expire after 10 minutes. The server stores only opaque blobs and SHA-256 hashes of the tokens.

**Auth**: members authenticate with a 32-byte random token, BCrypt-hashed on server, sent as `Authorization: AGMember {memberId} {token}`.

**Real-time**: Location is sent over WebSocket (`/api/ws/anonymous-groups/{id}/send-location`) and received via SSE (`/api/anonymous-groups/{id}/locations`). The server relays via Redis pub/sub on channel `ag-{groupId}` without decrypting.

**Redis** (Valkey, referred to as Redis throughout the codebase): used for the invite mailbox (`invite:{id}`, `invite-nameplate:{n}`, `invite-claim:{id}`, `invite-join:{groupId}:{tokenHash}`, 10-min TTL), rate-limit counters (`rate:*`) and pub/sub location relay.

## Coding standards

### General
- No comments unless the WHY is non-obvious (hidden constraint, workaround, subtle invariant)
- No unused code, dead branches, or backwards-compatibility shims
- No error handling for scenarios that cannot happen; trust framework guarantees
- Validate only at system boundaries (user input, external APIs)

### Backend (Java / Spring Boot)
- Follow existing package structure: one package per domain concern under `com.peppeosmio.lockate`
- Use `@SecuredAGMember` for any endpoint that requires member authentication
- Never log or expose plaintext sensitive fields; treat all encrypted fields as opaque blobs in service/controller layers
- TDD: write tests before implementing any feature

### Mobile (Kotlin / Android)
- All crypto operations go through `CryptoService`; never call crypto primitives directly in ViewModels or UI
- Use `_events: MutableSharedFlow<AnonymousGroupEvent>` for cross-layer communication; do not add new state channels without a clear reason
- Room stores decrypted data only; never persist ciphertext or IVs in Room tables

## Backend

### Commands

```bash
cd backend

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

`com.peppeosmio.lockate` splits into: `anonymous_group/` (controllers, service, entities, repos, DTOs, mappers, websocket, security, jobs), `invite/` (J-PAKE invite mailbox and join tokens), `api_key/`, `config/`, `redis/`.

The `@SecuredAGMember` annotation marks endpoints that require a valid member token. Auth is resolved in `AGMemberArgumentResolver` and passed to controllers as `AGMemberAuthentication`.

The `AnonymousGroupService` is the main orchestration point.

Location saves are throttled server-side via a `TTLMap` - a member cannot write a new location record more often than `lockate.retention.anonymous-group.location.save-interval` (default `PT2M`).

## Mobile

### Commands

```bash
cd mobile_app

# Debug build
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew testDebug --tests "com.peppeosmio.lockate.ExampleUnitTest"

# Install on connected device
./gradlew installDebug
```

The first clone needs `git submodule update --init --recursive` (Mbed TLS), and building needs the Android NDK and CMake 3.22.1 (Gradle installs them if the SDK licenses are accepted) plus a host `cmake` for `:jpake:jvmTest`.

### Structure

`com.peppeosmio.lockate` splits into: `ui/screens/` (6 screens), `service/` (AnonymousGroupService, InviteService, CryptoService, ConnectionService, LocationService), `data/` (Room entities, remote DTOs, mappers), `domain/`, `dao/`, `di/` (Koin modules), `android_service/` (background location foreground service).

`AnonymousGroupService` owns all networking. It holds a `_events: MutableSharedFlow<AnonymousGroupEvent>` that ViewModels collect for state changes. WebSocket reconnection uses exponential backoff (max 50s).

`CryptoService` uses the `whyoleg/cryptography` Kotlin library for AES-GCM and group key generation. `InviteExchange` is Lockate's invite protocol on the generic `:jpake` module and `InviteService` drives it over HTTP (`:jpake` is Kotlin Multiplatform; the native part is `:jpake-native`, and an iOS client would call the same C code through cinterop). Both are singletons via Koin.

The Room database stores decrypted data locally (group name, member name, coordinates) for UI use. The encryption key is stored encrypted in the `anonymous_group` table (`keyCipher`, `keyIv`) using Android Keystore via `KeystoreService`.