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

A staging backend is just another Lockate server, with its own database and Valkey; the app picks
the server at first run anyway.

The relay doesn't look at the app, so one relay serves both flavors when both Android apps are in
the same Firebase project. Terragrunt also has a separate `staging` environment (Google Cloud
project `lockate-staging`, its own Firebase project and relay), for testing changes to the relay
itself; a staging app built against it needs that project's Firebase values.

```
push_relay/terraform/
  modules/push-relay/     the resources
  live/root.hcl           shared: local state per environment, region, image from env
  live/prod/              project lockate
  live/staging/           project lockate-staging
```

`DOCKERHUB_USERNAME` and `RELAY_VERSION` pick the image; `terragrunt output url` gives the
environment's `PUSH_RELAY_URL`. State is local and not committed.
