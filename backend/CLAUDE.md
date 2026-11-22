# CLAUDE.md (backend)

Build/test rules specific to this module. See the repo-root `CLAUDE.md` for the security invariants, the coding standards and which `docs/` page to read for each area.

## Commands

```bash
# Format, check formatting, lint and test (CI runs `make ci`: the same without formatting)
make all

# Run all tests
./mvnw clean test -Pdev

# Run a single test class
./mvnw test -Pdev -Dtest=InviteServiceTest

# Start dev infra (PostgreSQL + Valkey), from the repository root
docker compose -f ../dev-docker-compose.yml up -d

# Run dev server (requires infra running)
./mvnw spring-boot:run -Pdev

# Build prod JAR
./mvnw clean package -Pprod
```

Run these from `backend/` (the Maven wrapper lives here).

The compose backend in the root `dev-docker-compose.yml` pushes through the staging relay on Cloud Run, for the staging app flavor. To run the relay locally instead, see `push_relay/CLAUDE.md`.

The backend reports its version and the API majors it serves at `GET /api/version`.
