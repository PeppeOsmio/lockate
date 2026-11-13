# Lockate push relay

Forwards push notifications from any Lockate backend to FCM with our Firebase project's
credentials, which can't be given to self-hosted backends. The payload is sealed by the backend
with a key only the app knows, so the relay can't read it. See `docs/background-sync-design.md`,
"Push relay", for why it exists and what it sees.

It is a Quarkus app compiled with GraalVM native-image, unlike the Spring Boot backend: it has no
state and almost no code, and Quarkus native starts in milliseconds, so it can scale to zero.

## API

`POST /v1/push` with `{"token": "<FCM token>", "data": {"r": "...", "iv": "...", "c": "..."}}`:

| Status | Meaning |
|---|---|
| 200 | FCM took it |
| 400 | No token, no data, or more than 8 entries / 3072 characters of data |
| 404 | FCM doesn't know the token any more: the backend drops the registration |
| 429 | Over 30 pushes a minute for the token, or 1200 for the caller's IP |
| 502 | FCM failed otherwise |

No authentication: knowing the token is the permission.

## Configuration

| Variable | Default | |
|---|---|---|
| `FCM_PROJECT_ID` | (required) | The Firebase project |
| `GOOGLE_APPLICATION_CREDENTIALS` | empty | A service-account key file; empty uses the metadata server (Cloud Run) |
| `RELAY_BEHIND_PROXY` | `false` | Take the caller's IP from `X-Forwarded-For` (Cloud Run, a reverse proxy) |
| `RELAY_PER_TOKEN_PER_MINUTE` | `30` | |
| `RELAY_PER_IP_PER_MINUTE` | `1200` | |
| `PORT` | `8080` | |

## Commands

```bash
# Format, check formatting, lint and test (CI runs `make ci`: the same without formatting)
make all

# Dev mode
FCM_PROJECT_ID=my-project ./mvnw quarkus:dev

# Native image
docker build -f native.Dockerfile -t lockate-push-relay .
```
