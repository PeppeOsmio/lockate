# Versioning

[← Architecture](architecture.md)

Lockate has four released components. Their versions say at a glance what works with what: **the
major version is the version of the API the component speaks**, the same number that is in its
URLs.

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

## What is a breaking change

Anything an older version of the other side can't understand: a removed or changed endpoint or
field, a different size or AAD of an encrypted field, a new push or frame format that old apps
can't open. A breaking change takes a new major **and** a new path: `/api/v2` with backend 2.0 and
app 2.0. The backend may serve `/api/v1` and `/api/v2` side by side for a while, so phones can
update at their own pace.

## Asking a server what it speaks

`GET /api/version` (no API version in the path, no API key needed) answers
`{"version": "1.4.0", "apis": [1]}`: the backend's version and every major of the Lockate API it
serves. The app asks it first when a server is added or edited and refuses one that doesn't serve
its API, saying which side needs an update; it shows the version in the server list and the hourly
[health check](architecture/health-check.md) notifies once when a server doesn't serve the app's
API: a server without the endpoint, or serving only older majors, needs an update; one serving only
newer majors needs a newer app.

## Pre-releases

A version can carry a pre-release suffix, in semver order:

| Suffix | Means |
|---|---|
| `-alpha.N` | Being built; anything may still change. |
| `-beta.N` | Complete, being tested; only fixes. |
| `-rc.N` | Candidate: the release unless a problem shows up. |

Until `1.0.0` is final, **the v1 API isn't frozen**: an alpha or beta may break what the previous
one spoke. Such a change is released on both sides at once (backend and app with the same version),
and the compatibility promise above starts with `1.0.0`. After that, pre-releases of a later version
(`1.3.0-beta.1`) keep it like any release.

Every tag, pre-release or not, is released from `main`. A pre-release:

- is published to Docker Hub only under its full version, never as `latest`, `X` or `X.Y`;
- is a GitHub pre-release for the app;
- is a version like any other for the relay, deployed where it should be tried (see
  [Push relay](architecture/push-relay.md#releasing-and-deploying)).

## Android version codes

Android updates an app only to a higher `versionCode`, so the code orders pre-releases too:

```
major × 100 000 000 + minor × 100 000 + patch × 1 000 + stage
```

with `stage` = 100 + N for `alpha.N`, 200 + N for `beta.N`, 300 + N for `rc.N` and 999 for a
release (N below 100, minor below 1000, patch below 100). `1.0.0-alpha.2` is 100 000 102, `1.0.0`
is 100 000 999.
