# Environments

[← Architecture](../architecture.md)

## App flavors

| Flavor | Application id | Name | Firebase config |
|---|---|---|---|
| `prod` | `com.peppeosmio.lockate` | Lockate | `lockate.firebase.*` / `LOCKATE_FIREBASE_*` |
| `staging` | `com.peppeosmio.lockate.staging` | Lockate Staging | `lockate.firebase.staging.*` / `LOCKATE_FIREBASE_STAGING_*` |

Different application ids make them separate apps: both install on one phone, each with its own
Room data, Keystore keys and FCM token, so a staging group never shows in prod. The Firebase
values (`projectId`, `appId`, `apiKey`, `senderId`) are public client config: they come from
`local.properties`, a Gradle property or the environment, and a flavor without them builds with no
pushes. Releases (`mobile-v*` tags) build `prod` only, with the values from the repository
variables; a staging APK is built locally (`./gradlew assembleStagingRelease`).

## Servers and relay

Each environment has its own Firebase project and relays, always paired: an FCM token belongs to
one project, so the staging app gets pushes only through a staging relay.

| | Prod | Staging |
|---|---|---|
| App | `com.peppeosmio.lockate` | `com.peppeosmio.lockate.staging` |
| Google Cloud / Firebase project | `lockate` | `lockate-staging` |
| Relays | Cloud Run | Cloud Run, and the dev Docker Compose |

The backend's `PUSH_RELAY_URL` defaults to the prod relay on Cloud Run, so a server set up from
the released images pushes to the released app with nothing to configure. The compose backend in
`dev-docker-compose.yml` uses the staging relay on Cloud Run instead, for the staging app flavor; a
backend run on the host sets `PUSH_RELAY_URL` to it. To work on the relay itself,
`docker compose --profile relay` also runs it in JVM dev mode (`localhost:3120`,
`FCM_PROJECT_ID=lockate-staging`, used with `PUSH_RELAY_URL=http://push-relay:8080`) with the key
of the `push-relay-dev` service account, which `terragrunt apply` in `live/staging` writes to
`push_relay/.secrets/service-account.json` (git-ignored). Keys exist only for staging; prod's relay
uses its Cloud Run identity. Several relays can serve one project: each holds the project's credentials,
and their rate limits are counted separately.

A staging backend is just another Lockate server, with its own database and Valkey; the app picks
the server at first run anyway.

Separate projects let us change the relay, its deployment or Firebase settings without touching
the people using the app, and a leaked development key reaches only staging installs. FCM works on
the free Spark plan; only Cloud Run needs Blaze. Both projects are on Blaze, under one billing
account.

```
push_relay/terraform/
  modules/push-relay/     the resources
  live/root.hcl           shared: local state per environment, region, image from env
  live/prod/              project lockate: Cloud Run
  live/staging/           project lockate-staging: Cloud Run and the dev key
```

`DOCKERHUB_USERNAME` and `RELAY_VERSION` pick the image (Cloud Run only); `terragrunt output url`
gives the environment's `PUSH_RELAY_URL`. State is local and not committed: it holds the dev key.
