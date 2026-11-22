# Roadmap

[← Architecture](architecture.md)

The architecture pages describe what is built. This page lists what isn't yet, and the limits we
accept for now.

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
- No notifications from a server without a push relay: notifications come only by push
  (`PUSH_RELAY_URL` empty sends none).
- A dead phone is reported as "stopped sharing" after 30 min.
- A still phone learns of a changed circle within 10 minutes, at its next post.
- A still position can be up to 10 min old, and a movement Activity Recognition misses is reported
  up to 10 min late.
