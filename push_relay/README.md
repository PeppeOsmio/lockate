# Lockate push relay

Forwards push notifications from any Lockate backend to FCM with our Firebase project's
credentials, which can't be given to self-hosted backends. The payload is sealed by the backend
with a key only the app knows, so the relay can't read it. See
[`docs/architecture/push-relay.md`](../docs/architecture/push-relay.md) for why it exists and what
it sees.

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

# In the dev Docker Compose, with the backend (JVM dev mode, on localhost:3120, for the staging
# Firebase project); `terragrunt apply` in terraform/live/staging writes its key first
PUSH_RELAY_URL=http://push-relay:8080 docker compose -f ../dev-docker-compose.yml --profile relay up

# Native image
docker build -f native.Dockerfile -t lockate-push-relay .
```

## Deploying with Terragrunt

`terraform/modules/push-relay` creates, in one Google Cloud project:

- with `cloud_run = true` (needs the Blaze plan): the `push-relay` service account (only the
  Firebase Cloud Messaging admin role, no key file), an Artifact Registry repository that proxies
  Docker Hub, and the public Cloud Run service;
- with `dev_key_file` set: a `push-relay-dev` service account with the same role and a key written
  to that file, for a relay run locally. The key is also in the state.

Terragrunt runs it per environment from `terraform/live/`: `prod` (project `lockate`, Cloud Run)
and `staging` (project `lockate-staging`, Cloud Run and the dev key in
`.secrets/service-account.json`).

```bash
gcloud auth application-default login
# Cloud Run only:
export DOCKERHUB_USERNAME=<the release workflow's DOCKERHUB_USERNAME>
export RELAY_VERSION=<release version, like 1.2.3>

cd terraform/live/staging   # or prod
terragrunt apply
```

`terragrunt output url` prints the environment's `PUSH_RELAY_URL`. The image exists once a
`relay-vX.Y.Z` release has run. The state is `terraform.tfstate` in the environment's folder (not
committed, and it holds the dev key): keep it, or switch `remote_state` in `live/root.hcl` to a GCS
bucket.
