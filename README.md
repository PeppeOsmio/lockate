# Lockate

Location sharing without the surveillance. End-to-end encrypted, zero knowledge. Only your circle can see.

Users join ephemeral groups without accounts - group names, member names, and GPS coordinates are all **end-to-end encrypted** before they ever reach the server.

## How it works

1. A user creates a group with a name and password - all encrypted client-side before being sent to the server.
2. Other users join by entering the group ID and password, picking a member name (also encrypted).
3. Authentication uses the **SRP6 protocol**: the password is never transmitted; only a cryptographic proof is exchanged.
4. Members share their GPS location in real-time. Coordinates are encrypted on-device before transmission.
5. The server stores only ciphertext - it never sees plaintext names, passwords, or coordinates.

---

## Features

- **No account required** - create or join a group with just a name, a password, and a member nickname
- **End-to-end encryption** - group names, member names, and GPS coordinates are encrypted on-device before ever leaving the phone
- **Zero-knowledge server** - the backend stores only ciphertext and never sees plaintext names, passwords, or coordinates
- **Real-time location sharing** - see all group members' positions on a live map, updated in real-time
- **Background location** - keeps sharing your location even when the app is in the background
- **Group management** - create groups, join by ID, leave, or delete
- **Self-hostable** - point the app at any self-hosted Lockate backend

## Development

### Backend

**Prerequisites:** Java 21+, Docker

Start the dev infrastructure (PostgreSQL + Valkey):

```bash
cd backend
docker compose -f dev-docker-compose.yml up -d
```

Run the dev server:

```bash
./mvnw spring-boot:run -Pdev
```

Run all tests:

```bash
./mvnw clean test -Pdev
```

Run a single test class:

```bash
./mvnw test -Pdev -Dtest=SrpServiceTest
```

**API key management** - run with the `cli` profile and pass the command as an argument:

```bash
./mvnw spring-boot:run -Pdev -Dspring-boot.run.profiles=cli -Dspring-boot.run.arguments="create-api-key"
./mvnw spring-boot:run -Pdev -Dspring-boot.run.profiles=cli -Dspring-boot.run.arguments="list-api-keys"
./mvnw spring-boot:run -Pdev -Dspring-boot.run.profiles=cli -Dspring-boot.run.arguments="delete-api-key <uuid>"
```

**Run a background job manually** - activate the job's profile, e.g. to run location retention cleanup:

```bash
./mvnw spring-boot:run -Pdev -Dspring-boot.run.profiles=job-location-retention
```

Build a production JAR:

```bash
./mvnw clean package -Pprod
```

## Tech Stack

The backend is built with **Spring Boot**, uses **PostgreSQL** for storage, and **Redis** for real-time messaging. The Android app is built with **Jetpack Compose**.

---

## License

MIT