# Versioning

[← Architecture](architecture.md)

Lockate has four released components. Their versions say at a glance what works with what: **from
1.0.0 on, the major version is the version of the API the component speaks**, the same number that
is in its URLs.

## The interfaces

| Interface | Between | Path |
|---|---|---|
| Lockate API: REST, SSE, the encrypted formats, sealed pushes | app ↔ backend, and app ↔ app in one group | `/api/v1` |
| Relay API | backend → push relay | `/v1/push` |
| Admin API | admin UI → backend | `/api/v1/admin`, released together |

## The components

| Component | Tag | Major = |
|---|---|---|
| Backend | `backend-vX.Y.Z` | the Lockate API it serves |
| Admin UI | the backend's tag, same version | the backend's |
| App | `mobile-vX.Y.Z` | the Lockate API it speaks |
| Push relay | `relay-vX.Y.Z` | the relay API it serves |

- **Same major, compatible.** An app 1.x works with any backend 1.x, two apps 1.x understand each
  other in a group, and a backend 1.x pushes through any relay 1.x.
- **Minor** adds what older versions of the same major can live without: an app 1.7 on a backend
  1.2 works, and a feature the backend doesn't have yet answers 404, which the app handles.
- **Patch** fixes without changing what the component speaks.
- Backend, app and relay are released apart: a fix in the app alone is a new `mobile-v` tag only.

Versions are plain `X.Y.Z`, with no pre-release suffixes.

## Before 1.0.0

The 0.x versions are the early ones: they speak `/api/v1` and `/v1/push`, but **those APIs aren't
frozen yet**, and any minor may break what the previous one spoke. Such a change is released on
both sides at once, backend and app with the same new minor (0.7.0 and 0.7.0), so during 0.x the
safe pair is a backend and an app of the same minor. The compatibility promise above starts with
1.0.0, when the v1 APIs are frozen.

## What is a breaking change

Anything an older version of the other side can't understand: a removed or changed endpoint or
field, a different size or AAD of an encrypted field, a new push or frame format that old apps
can't open. From 1.0.0 on, a breaking change takes a new major **and** a new path: `/api/v2` with
backend 2.0 and app 2.0. The backend may serve `/api/v1` and `/api/v2` side by side for a while, so
phones can update at their own pace.

## Asking a server what it speaks

`GET /api/version` (no API version in the path, no API key needed) answers
`{"version": "1.4.0", "apis": [1]}`: the backend's version and every major of the Lockate API it
serves (a 0.x backend answers `[1]` too). The app asks it first when a server is added or edited
and refuses one that doesn't serve its API, saying which side needs an update; it shows the version
in the server list and the hourly [health check](architecture/health-check.md) notifies once when a
server doesn't serve the app's API: a server without the endpoint (older than 0.7.0), or serving
only older majors, needs an update; one serving only newer majors needs a newer app.

## Releasing

Every tag is released from `main`. Each release is published to Docker Hub under `X.Y.Z`, `X.Y`,
`X` and `latest` (backend, admin UI, relay) or as a GitHub release with the APKs (app). The relay is
then deployed by hand (see [Push relay](architecture/push-relay.md#releasing-and-deploying)).

## Android version codes

Android updates an app only to a higher `versionCode`:

```
major × 1 000 000 + minor × 1 000 + patch
```

(minor and patch below 1000): `0.7.0` is 7 000, `1.0.0` is 1 000 000.
