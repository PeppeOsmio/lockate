# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Lockate is a zero-knowledge, end-to-end encrypted real-time location sharing app. The server stores only ciphertext - it never sees plaintext group names, member names, passwords, or GPS coordinates.

## Security invariants - never violate these

- All sensitive fields (group name, member names, coordinates) are encrypted **client-side** before leaving the device. The server must never receive or log plaintext versions of these.
- The group password is never transmitted or stored. SRP6 verifier + salt are stored instead.
- Members send location updates even when stationary, so the server cannot infer stillness from silence.
- The derived AES-GCM key never leaves the client. The server stores only the PBKDF2 salt.
- Every new entity or field added to the database must follow the same zero-knowledge model.

## Architecture

Three core domain entities:
- **AnonymousGroup** - password-protected group; stores encrypted name, SRP verifier/salt, PBKDF2 key salt
- **AGMember** - user scoped to one group; stores encrypted name, BCrypt token hash, admin flag
- **AGMemberLocation** - encrypted GPS record; stores ciphertext + IV, never plaintext coordinates

**Encryption**: AES-GCM, key = PBKDF2-SHA256(password, keySalt, 600K iterations, 256-bit). Each field has its own random 12-byte IV.

**Auth**: SRP6 (RFC 5054, 2048-bit group, SHA-256). Three-step handshake - client sends A, server returns B + session ID (stored in Redis with 5-min TTL), client sends M1 proof, server returns member token. Token is 32 random bytes, BCrypt-hashed on server.

**Real-time**: Location is sent over WebSocket (`/api/ws/anonymous-groups/{id}/send-location`) and received via SSE (`/api/anonymous-groups/{id}/locations`). The server relays via Redis pub/sub on channel `ag-{groupId}` without decrypting.

**Redis** (Valkey, referred to as Redis throughout the codebase): used for SRP session storage (`srp:{uuid}`, 5-min TTL) and pub/sub location relay.

## Backend commands

```bash
cd backend

# Run all tests
./mvnw clean test -Pdev

# Run a single test class
./mvnw test -Pdev -Dtest=SrpServiceTest

# Run dev server (requires infra running)
./mvnw spring-boot:run -Pdev

# Start dev infra (PostgreSQL + Valkey)
docker compose -f dev-docker-compose.yml up -d

# Build prod JAR
./mvnw clean package -Pprod
```

## Mobile commands

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

## Backend structure

`com.peppeosmio.lockate` splits into: `anonymous_group/` (controllers, service, entities, repos, DTOs, mappers, websocket, security, jobs), `srp/` (SRP6 server-side logic), `api_key/`, `config/`, `redis/`.

The `@SecuredAGMember` annotation marks endpoints that require a valid member token. Auth is resolved in `AGMemberArgumentResolver` and passed to controllers as `AGMemberAuthentication`.

The `AnonymousGroupService` is the main orchestration point. The `StatelessSRP6Server` extends BouncyCastle's SRP6Server to allow session state to be serialized to Redis.

Location saves are throttled server-side via a `TTLMap` - a member cannot write a new location record more often than `lockate.retention.anonymous-group.location.save-interval` (default `PT2M`).

## Mobile structure

`com.peppeosmio.lockate` splits into: `ui/screens/` (6 screens), `service/` (AnonymousGroupService, CryptoService, SrpClientService, ConnectionService, LocationService), `data/` (Room entities, remote DTOs, mappers), `domain/`, `dao/`, `di/` (Koin modules), `android_service/` (background location foreground service).

`AnonymousGroupService` owns all networking. It holds a `_events: MutableSharedFlow<AnonymousGroupEvent>` that ViewModels collect for state changes. WebSocket reconnection uses exponential backoff (max 50s).

`CryptoService` uses the `whyoleg/cryptography` Kotlin library (not BouncyCastle) for AES-GCM. SRP6 client uses BouncyCastle. Both services are singletons via Koin.

The Room database stores decrypted data locally (group name, member name, coordinates) for UI use. The encryption key is stored encrypted in the `anonymous_group` table (`keyCipher`, `keyIv`) using Android Keystore via `KeystoreService`.