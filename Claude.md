# Lockate – Backend

## Stack
- Spring Boot, PostgreSQL (JPA/Hibernate), Redis
- Java, Maven

## Security Rules
- Never log, store, or transmit plaintext sensitive data (names, coordinates)
- Never suggest changes that expose plaintext to the server
- Group password never stored - SRP verifier + salt stored instead (zero-knowledge proof)
- Every entity in the database must not give the server any clue about non-anonymous data (user and group names, locations)
- Users still send their location even if still so that the server cannot detect that they are still
- All sensitive fields encrypted client-side with a key derived from the group password

## Entities
- **Anonymous Group (AG):** password-protected group
- **AG Member:** user scoped to one Anonymous Group (AG)
- **AG Member Location:** encrypted location record

## Redis

Valkey is used instead of Redis but will be referred to as Redis.
Usage:
- Store SRP sessions with 5 minutes of TTL with key value
- Exchange new locations of an AG via pub/sub
