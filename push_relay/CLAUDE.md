# CLAUDE.md (push_relay)

Build/test rules specific to this module. See the repo-root `CLAUDE.md` for the security invariants and the coding standards, `docs/architecture/push-relay.md` for why the relay exists and what it sees, and `README.md` here for running and deploying it.

## Commands

```bash
# Format, check formatting, lint and test (CI runs `make ci`: the same without formatting)
make all

# Dev mode
FCM_PROJECT_ID=my-project ./mvnw quarkus:dev
```

Run these from `push_relay/` (the Maven wrapper lives here).

It is Quarkus compiled native, not Spring Boot: it has no state and must start in milliseconds to scale to zero. No Firebase Admin SDK: the access token comes from the metadata server on Google Cloud or from a service-account key file (`GOOGLE_APPLICATION_CREDENTIALS`), signed by hand.

To run it with the rest of the dev stack, from the repository root: `docker compose -f dev-docker-compose.yml --profile relay up` (`push-relay`, JVM dev mode, `localhost:3120`, staging Firebase project; the backend uses it with `PUSH_RELAY_URL=http://push-relay:8080`). It needs the key that `terragrunt apply` in `terraform/live/staging` writes to `.secrets/service-account.json` (git-ignored); without it pushes fail with 502 and everything else works.
