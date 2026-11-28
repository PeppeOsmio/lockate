# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Lockate is a zero-knowledge, end-to-end encrypted real-time location sharing app. The server stores only ciphertext - it never sees plaintext group names, member names, group keys, or GPS coordinates.

The design lives in `docs/`, and it is the only place it is written down: `docs/architecture.md` is the map, each component has its page in `docs/architecture/` with the reasons behind every decision, and `docs/roadmap.md` lists what isn't built yet and the limits we accept. Read the page of the area you work on before changing it, and keep the docs current with every change.

| Before touching | Read |
|---|---|
| Anything encrypted (fields, AADs, sizes, keys on the phone) | `docs/architecture/encryption.md` |
| Groups, members, member tokens, API keys, retention | `docs/architecture/groups-and-auth.md` |
| The admin API or `admin_ui/` | `docs/architecture/admin.md` |
| Invites, J-PAKE, `mobile_app/jpake*` | `docs/architecture/invites.md` |
| Places, geofencing, place events, notifications | `docs/architecture/places.md` |
| Sending positions: still/moving, wake locks, Doze, the locations endpoint | `docs/architecture/sending.md` |
| Who is sharing, presence, map colours | `docs/architecture/sharing.md` |
| The SSE stream, frames, Redis pub/sub | `docs/architecture/realtime.md` |
| Push registration and sealed pushes | `docs/architecture/push.md` |
| `push_relay/` | `docs/architecture/push-relay.md` |
| The hourly health check | `docs/architecture/health-check.md` |
| App flavors, Firebase projects, staging | `docs/architecture/environments.md` |
| The app's theme, colours and shared components | `docs/architecture/app-ui.md` |
| Versions and releases | `docs/versioning.md` |

## Security invariants - never violate these

- All sensitive fields (group name, member names, coordinates) are encrypted **client-side** before leaving the device. The server must never receive or log plaintext versions of these.
- The group key is 32 random bytes generated client-side. It reaches new members only inside an invite payload encrypted with a key derived from the J-PAKE shared key.
- Every encrypted field has a fixed size, so its length tells the server nothing. The server may learn *when* members send (stillness from a still member posting every 10 minutes, place crossings from place events), never what: coordinates, activity, place ids and enter/exit stay encrypted.
- The server relays invite messages as opaque blobs and stores join tokens only as SHA-256 hashes.
- Every new entity or field added to the database must follow the same zero-knowledge model.

## Modules

| Module | What | Commands |
|---|---|---|
| `backend/` | Spring Boot API, PostgreSQL and Valkey (called Redis in the code) | `backend/CLAUDE.md` |
| `mobile_app/` | Android app, plus the `jpake` / `jpake-native` J-PAKE library | `mobile_app/CLAUDE.md` |
| `admin_ui/` | React admin UI, served next to the backend | `admin_ui/CLAUDE.md` |
| `push_relay/` | Quarkus native app that forwards sealed pushes to FCM | `push_relay/CLAUDE.md` |

Every module has `make all`: it formats the code, then checks formatting, lints and tests. CI runs `make ci`, the same without formatting.

## Coding standards

### General
- No comments unless the WHY is non-obvious (hidden constraint, workaround, subtle invariant)
- No unused code, dead branches, or backwards-compatibility shims
- No error handling for scenarios that cannot happen; trust framework guarantees
- Validate only at system boundaries (user input, external APIs)
- Before committing, run `make all` in each module you changed

### Backend (Java / Spring Boot)
- Follow existing package structure: one package per domain concern under `com.peppeosmio.lockate`
- Use `@SecuredAGMember` for any endpoint that requires member authentication
- Never log or expose plaintext sensitive fields; treat all encrypted fields as opaque blobs in service/controller layers
- TDD: write tests before implementing any feature

### Mobile (Kotlin / Android)
- All crypto operations go through `CryptoService`; never call crypto primitives directly in ViewModels or UI
- Use `_events: MutableSharedFlow<AnonymousGroupEvent>` in `AnonymousGroupService` for cross-layer communication; do not add new state channels without a clear reason
- Room stores decrypted data only; never persist ciphertext or IVs in Room tables

## Releases

Versions follow `docs/versioning.md`: plain `X.Y.Z`, and from `1.0.0` on a component's major is the version of the API it speaks (`/api/v1` for backend and app, `/v1/push` for the relay). The 0.x versions may break the v1 APIs in any minor, and backend and app then release the same new minor together. Every tag is released from `main`:

- `backend-vX.Y.Z` → `.github/workflows/release-backend.yml` publishes `lockate-backend` and `lockate-admin-ui` to Docker Hub with the same version.
- `mobile-vX.Y.Z` → `.github/workflows/release-mobile.yml` attaches the signed APKs to a GitHub release, with `versionCode` = major × 1000000 + minor × 1000 + patch.
- `relay-vX.Y.Z` → `.github/workflows/release-relay.yml` publishes `lockate-push-relay`; deploying it is a Terragrunt apply with that version.

Release workflows only build and publish; the `checks-*` workflows run on pull requests only.
