# Roadmap

[← Architecture](architecture.md)

The architecture pages describe the target; this page says how far the code is. Each step is one
or more PRs, shippable on its own, ordered so notifications keep working at every step.

## Background sync redesign

Goal: a still phone keeps reporting on time in Doze, and notifications arrive without a socket
held open ([Sending positions](architecture/sending.md#why-the-location-request-is-the-timer)).

| Step | | Status |
|---|---|---|
| 1 | Cleanup: drop the exact-alarm experiment. | done |
| 2 | SHA-256 member tokens ([details](architecture/groups-and-auth.md#member-tokens)). A migration deletes every group; the app shows them as deleted. | done, not released |
| 3a | Backend: `POST …/locations` and `…/members/pause`, rate limit, sharing timer 30 min. The socket still accepts the old messages. | done, not released |
| 3b | App: every send over REST, still fix every 10 min, circle events with the position, colours 15 / 30 min. | done, not released |
| 3c | Backend: the socket accepts only `ping`. Released after the 3b app. | done, not released |
| 4a | Push registration (app and backend). | done, not released |
| 4b | Push relay module, image and Terraform/Terragrunt. | done, deployed on staging and prod |
| 4c | Pushes: backend seals and sends, app opens and notifies. | done, not released |
| 5a | Fetch on a miss: a push naming a member or circle the phone hasn't saved fetches the group's snapshot and notifies after it ([details](architecture/push.md#members-and-circles-the-phone-doesnt-know-yet)). Needed before 5b, and fixes a rare race today. | done, not released |
| 5b | Switch-over: foreground service only while a group is shared; group events on the SSE stream; `AGWSHandler` removed; location posts answer the circles version ([details](architecture/sending.md#keeping-the-circles-current)). | done, not released; release only with the relay deployed, or nobody gets notifications |
| 5c | Conditional location post: events computed on circles the server no longer has are refused (409) and computed again; a changed circle is seeded from the last position the server got ([details](architecture/sending.md#keeping-the-circles-current)). | done, not released |
| 6 | Health check: about every hour (WorkManager, with a network) each group's membership is checked and the pushes registered again; a server that doesn't speak the app's API is notified once ([details](architecture/health-check.md)). | done, not released |

The relays are deployed, and a backend pushes through the prod one by default (`PUSH_RELAY_URL`).

Since 5b notifications come only by push: a server with an empty `PUSH_RELAY_URL` sends none.

## Open points

- **Relay hosting**: Cloud Run is set up; an EU VPS remains the alternative if we'd rather Google
  didn't see which servers push ([Push relay](architecture/push-relay.md)).
- **Binding a token to its backends** (Nextcloud-style): each backend has an Ed25519 key pair, the
  app tells the relay which keys may push to its token, backends sign requests. A stolen token alone
  would be useless, at the cost of state in the relay, the app talking to the relay (which then sees
  the phone's IP) and a verification push. Can be added later without changing the payload.
- **Persisting the tracker's confirmed circle state** in Room, so a transition pending when the
  process dies is still notified ([Circles](architecture/circles.md)).
- **UnifiedPush** for phones without Google Play services, with the same encrypted payload.

## Accepted trade-offs

- No notifications without Google Play services, and no F-Droid build.
- Old app versions stop working against the new server: the v1 API isn't frozen before 1.0.0, so
  the redesign is released as 0.7.0 of both backend and app ([Versioning](versioning.md)).
- Every existing group was deleted by the SHA-256 change.
- A dead phone is reported as "stopped sharing" after 30 min.
- No notifications from a server without a push relay (no socket to fall back on since 5b).
- A still phone learns of a changed circle within 10 minutes, at its next post.
- A still position can be up to 10 min old, and a movement Activity Recognition misses is reported
  up to 10 min late.
