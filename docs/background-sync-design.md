# Background sync redesign

Status: direction agreed. Steps 1, 2 and 3a done (not yet released), 3b next (see Timeline).

This document is the reference for how background sync works and why: every decision and its
reasons are here, and it is updated together with the code.

## Problem

A still phone must keep telling the server it is sharing. Today the still heartbeat and the socket
pings are timed on the phone with a coroutine `delay`. Once the GPS is off nothing wakes the CPU,
and `delay` doesn't count the time the CPU sleeps, so in Doze the heartbeat arrives minutes late,
the 5-minute presence timer expires and the group sees "stopped sharing".

Considered and dropped:

- **Exact `AlarmManager` alarm every minute**: needs the "Alarms & reminders" permission, denied by
  default on Android 14+; the inexact alarm is only guaranteed within an hour.
- **Server pings over the socket** (the arriving packet wakes the phone): keeps a foreground
  service and a socket open even for groups we don't share with, which is what FCM is for, and a
  `location` foreground service that uses no location is a Play policy risk.
- **WorkManager**: periodic work is limited to 15 minutes and deferred by design.
- **WebSocket while moving**: works, but needs its own liveness rules (pings, closing after a
  silent period) and a switch between socket and REST. Posting every position over REST on a
  reused connection costs about the same and leaves one way to send.

## Design

| Situation | Transport |
|---|---|
| Moving | GPS every ~2 s, each position posted over REST on a reused connection. |
| Still | GPS (high accuracy) every 10 min, posted over REST. |
| Sending anything | REST only, no WebSocket at all. A position and the circle events it caused go in one request; `member.paused` on its own. |
| Not sharing in any group | No foreground service. |
| Notifications: circle events, member started/stopped sharing, removed from a group, group deleted | FCM only, encrypted payload, through our push relay. |
| App open | SSE stream of the group on screen only: positions and real-time events (members, circles, group). |
| Health | Inexact alarm every hour: health check of each server, local notification if one is down. |

**Map dot and "stopped sharing"**: green ≤ 15 min since the last position, yellow 15–30 min, grey
after 30 min. "Stopped sharing" is pushed at the same moment: the sharing timer is 30 min.

Why these numbers, with a still fix every 10 min:
- A fix on time keeps the dot green (at most 10 min old); one up to 5 min late (Fused in Doze)
  still does.
- One lost fix (indoors, no network) turns it yellow at 15 min, and the next one, at 20, makes it
  green again.
- Grey and "stopped sharing" at 30 min take about two lost fixes in a row, rare enough not to raise
  false alarms.
- 10 rather than 5 min halves the GPS wake-ups and the requests while still, the main cost then.
  The price: a still position up to 10 min old, and, when Activity Recognition misses a movement
  (a train, a smooth car), positions and circle events up to 10 min late. Once AR sees the
  movement the GPS runs every 2 s and the interval no longer matters. The interval is one
  constant, so it can go back to 5 min if 10 turns out too long.

## Details

**Still mode**
- The wake-up is the location request itself: Fused, high accuracy, 10-minute interval, inside the
  foreground service (Fused delivers in Doze to a location foreground service).
- On each fix, a short wake lock until the `POST` is sent.
- `pickGoodFix` stays: an inaccurate indoor fix doesn't replace the last good one.

**Moving mode**
- Each GPS fix is posted at once (the current 1.5 s throttle stays). Posts go one at a time, so
  they reach the server in order.
- OkHttp keeps the connection open between posts (HTTP/2 when the server or its reverse proxy
  offers it over TLS, otherwise HTTP/1.1 keep-alive), so a post costs about as much as a socket
  frame. The reverse proxy must allow that keep-alive.
- No fix for a while (a tunnel): nothing is posted; there is no connection state to clean up.
- The per-member rate limit on the endpoint must allow one post every ~1.5 s.

**Positions and circle events in one request**
- A circle event only ever comes from a position (`GeofenceTracker.update(fix)`), so the request
  that posts a position also carries the circle events it caused:
  `POST {encryptedCoordinates, encryptedActivity, circleEvents: [encryptedEvent, …]}`. The server
  handles the position first, then each event in order. `member.paused` is the only other send.
- No queue (`CircleEventQueue` goes): the tracker computes transitions against the **last state
  the server confirmed** (the circles it knows we are in), not the last state seen. A successful
  request moves the confirmed state forward; a failed one changes nothing, so the next position
  recomputes and resends the same transitions by itself.
- An enter and exit both made while offline cancel out and are never sent.
- A transition waiting for more than 5 minutes is confirmed without being sent, since receivers
  use the server's time and a late "arrived home" would be wrong (same 5-minute rule as today).
- The confirmed state lives in memory, like the tracker today: after the process restarts the
  tracker reseeds silently from the first fix, and a transition pending at that moment, or made
  while the process was dead, is not notified. Persisting it in Room is a possible later
  improvement.
- Server: at most a small number of events per request (e.g. 16), each accepted only at today's
  exact size. The server learns how many crossings a request carries, which it already learns
  today from separate frames.

**Sharing timer (Redis)**
- Every posted position refreshes the timer, 30 min.
- Every backend instance hears the timer's expiry, but "stopped sharing" must be pushed once. Keep
  the current two-key scheme: the expiring timer key, plus a second key holding "announced as
  sharing" that one instance claims with an atomic `GETDEL` before pushing. "Started sharing" is
  pushed by the position that finds the second key empty (`SET … GET`).

**Authentication: SHA-256 instead of BCrypt**
- Today a member's token is stored BCrypt-hashed, and BCrypt takes ~50–100 ms of CPU per check:
  fine once per socket, too much for a post every 2 s per moving member.
- BCrypt's slowness protects guessable passwords. Member tokens are 32 random bytes generated by
  the app (256 bits), impossible to guess with any hash, so SHA-256 is as safe and costs
  microseconds. The invite join tokens already use SHA-256.
- The server stores `SHA-256(token)` and compares with `MessageDigest.isEqual` (constant time).
- No cache needed: every request checks the database directly, and a removed member fails at once.

**FCM registration and payload**
- The app has one FCM token per phone (one Firebase project), the same for every server, so the
  token can't tell the app which server or group a push is for. A registration id does.
- Per member (one group on one server) the app generates a random registration id (16 bytes) and a
  random 32-byte AES key, and stores them in Room with the server, group and member; the AES key
  is encrypted with the Android Keystore, like the group key. It registers
  `{fcmToken, registrationId, key}` with the backend (`@SecuredAGMember`). Re-registered when FCM
  changes the token (`onNewToken`) and by the health check. Leaving a group deletes the row, and the
  backend's registration with the member.
- The backend stores the registration (token, registration id, key) with the member: a device
  identifier and a key, never plaintext group data.
- FCM `data` message, high priority:

  ```
  { "r": registrationId, "iv": 12 bytes, "c": AES-GCM(key, payload, aad = "lockate-push-v1|" + r) }
  ```

  `r` in the AAD stops a valid payload from being replayed under another registration.
- Payload: event type, the event (circle events stay encrypted with the group key as today),
  server timestamp. Group and member are known from `r`. Well under FCM's ~4 KB limit, so nothing
  is stored server-side for later fetch.
- On receipt (`onMessageReceived`): look up `r` in Room, drop the push if unknown (left group, old
  registration, forged); decrypt with its key, drop if that fails; then show the notification for
  that group. AES-GCM also authenticates: only the backend and the app hold the key, so neither the
  relay nor someone holding a stolen token can make the app show anything.
- Removed from a group / group deleted: a push to the affected members, so the app cleans up even
  while closed.

**Health check (hourly, inexact)**
- Server reachable, member still valid, FCM registration present (re-register if not).
- Local notification only after 2–3 consecutive failures, and once, so an hour without network
  isn't an alarm.

**Other**
- On boot the foreground service restarts only if a group is shared.
- `CLAUDE.md`: the privacy invariant changes (still members send every 10 min), the Auth section
  changes (SHA-256), and the FCM token is new stored data: a device identifier, not sensitive in
  the zero-knowledge sense.

## Push relay

**Why it's needed.** The published app contains the configuration of one Firebase project, ours.
The FCM tokens it gets belong to that project, and FCM accepts sends to them only with an OAuth
token of that project's service account (the legacy server key that could send on its own was
shut down in 2024). The client config in the app (`apiKey`, `appId`, `senderId`) only receives.
The service-account key is a secret: anyone holding it can push to every phone with the app, use up
the project's quota or get it suspended, and FCM permissions can't be limited to some tokens. So
it can't be shipped to self-hosted backends, and a self-hosted backend can't talk to FCM directly
for our app. Same scheme as Matrix (Sygnal) and Nextcloud's push proxy.

**What it is.** A small HTTP service we host that holds the credentials for our Firebase project
and forwards pushes:

```
backend (any server)                relay (ours)                    FCM            phone
  POST /v1/push  ───────────────▶  check limits, add credentials ─▶ send ────────▶ wake app,
  {token, data, priority}                                                          decrypt data
            ◀─── 404 if FCM says the token is unregistered (backend deletes the registration)
```

- `data` is the payload already encrypted by the backend with the app's AES key. The relay
  can't read it.
- The relay sees: the FCM token, the calling backend's IP, when and how often it pushes, the
  registration id and the payload size. Not groups, members or contents.
- The relay sees that several registration ids go to the same token (the same phone), and from which
  backend IPs; Google already sees that through FCM.

**How a backend authenticates: it doesn't, the token is the permission** (like Sygnal)
- The relay forwards any request carrying a token. FCM tokens aren't public: only the app and the
  backends the user registered with know them, and those backends may push anyway.
- A leaked token (compromised backend, a log) lets someone wake that phone, but not show anything:
  without the AES key the app drops the push. The relay limits payload size and rate per token and
  per source IP, so the damage is bounded.
- No sign-up, no per-backend keys, no state in the relay.
- Our own hosted backend uses the same relay (one code path), or FCM directly with the key since
  it runs next to it.

**Considered and dropped**
- *An API key per backend, issued by us*: needs a sign-up and a key database, and tells us who
  self-hosts.
- *Giving backends our service-account key*: see "Why it's needed".
- *A Firebase project per server, its client config passed to the app at runtime* (one
  `FirebaseApp` per server, no relay): getting an FCM token from a secondary `FirebaseApp` isn't
  documented and could break with any SDK update, and every self-hoster would have to set up
  Firebase.

**Backend configuration**
- `lockate.push.relay-url`: default our relay; a self-hoster who builds their own app with their
  own Firebase project can run their own relay and point here.
- Without a reachable relay, the server still works; only notifications are missing.

**How it's built**
- A new module in this repo, `push_relay/`: Quarkus compiled native (starts in milliseconds, so it
  can scale to zero), its own Docker image, released with the backend under the same version.
- No Firebase Admin SDK (heavy and awkward in a native image): an access token (from the Cloud Run
  metadata server, or a JWT signed with the service-account key elsewhere) and a `POST` to FCM's
  HTTP v1 `messages:send`.
- Same conventions as the other modules: `make all` / `make ci`, formatting and checkstyle like the
  backend, a `checks-push-relay.yml` workflow on pull requests, a `CLAUDE.md` section and a short
  README saying why it uses a different framework.

**Where it runs** (to confirm when we get there)
- Preferred: Google Cloud Run in the Firebase project. Free tier, scales to zero, and it calls FCM
  with its own service identity, so there is no key file to guard. Google learns the calling
  backends' IPs on top of the push metadata it already sees through FCM.
- Alternative: a small EU VPS without logs, with the service-account key as a secret, if we'd
  rather Google didn't see which backends push.
- The default `lockate.push.relay-url` points at a domain of ours, so moving between the two
  changes neither backends nor apps.

## Open points

- **Relay hosting**: Cloud Run in the Firebase project or an EU VPS (see "Where it runs"). Decide
  in step 4.
- **Binding a token to its backends** (Nextcloud-style): each backend has an Ed25519 key pair, the
  app tells the relay which public keys may push to its token, backends sign requests. A stolen
  token alone would then be useless, at the cost of state in the relay, the app contacting the
  relay directly (the relay sees the phone's IP), and a verification push to prove the app owns the
  token. Can be added later without changing the payload.
- **Persisting the tracker's confirmed circle state** in Room, so a transition pending when the
  process dies is still notified.
- **UnifiedPush** for phones without Google Play services, with the same encrypted payload.

## Accepted trade-offs

- No notifications on phones without Google Play services, and no F-Droid build (it rejects
  Firebase). UnifiedPush can be added later with the same encrypted payload.
- Old app versions stop working against the new server; backend and app are released together.
- Every existing group is deleted when the SHA-256 change ships (step 2).
- A dead phone is reported as "stopped sharing" after 30 min.

## Timeline

Each step is one PR (backend and app together where both change), shippable on its own. The order
keeps notifications working at every step: the WebSocket stays, for receiving only, until FCM
replaces it.

1. **Cleanup.** Drop the uncommitted `MinuteAlarm` / exact-alarm work; `main` stays as it is.
2. **SHA-256 member tokens.** Backend stores and checks `SHA-256(token)`. Existing BCrypt hashes
   can't be converted without the tokens, so a Liquibase changeset deletes every group (with its
   members, locations and circles); users create their groups again. The app needs no change: the
   404 it then gets marks each group as deleted, as for any deleted group.
3. **Sending over REST**, in two PRs so the backend can ship first without breaking any app.
   - **3a, backend only.** Old apps keep working: the socket still accepts `member.location`,
     `circle.event` and `member.paused`.
     - `POST /api/v1/anonymous-groups/{id}/locations` (`@SecuredAGMember`, same path as the SSE
       stream, which is a `GET`):
       `{encryptedCoordinates, encryptedActivity, circleEvents: [{cipherText, iv}, …]}`.
       `circleEvents` may be missing or empty. Everything is checked before anything is relayed:
       at most 16 events, each of the exact circle-event size, then the position (same checks as
       today). Then the position is relayed and maybe saved exactly as a socket `member.location`
       (the save throttle applies), presence is refreshed, and each event is published in order
       as a `circle.event` frame. A bad request is a 400 and relays nothing.
     - `POST /api/v1/anonymous-groups/{id}/members/pause`: what `member.paused` does today.
     - Rate limit: at most 120 location posts per member per minute (`rate:location:{memberId}`),
       429 above. The app sends at most one every 1.5 s (40 a minute), so only a broken or
       malicious client hits it. The socket isn't limited, as today.
     - Both live in `AGMemberMessageService`, which keeps its own save-throttle cache. The socket
       keeps its own until 3b removes that path; a member uses one or the other, never both.
     - Sharing timer: `ag-presence` 30 min, `ag-online` 31 min (from 5 and 6). Old apps, which
       still send every minute while still, are then reported "stopped sharing" after 30 min
       instead of 5.
   - **3b, app, and the socket stops accepting member messages.**
     - App: every send goes over REST, one post at a time on the shared `HttpClient`, a failed
       post dropped (the next fix replaces it). Moving: each fix posted. Still: Fused high
       accuracy every 10 min + `POST` + short wake lock; `PingClock` no longer times the still
       heartbeat. Circle events ride with the position, against the server-confirmed state;
       `CircleEventQueue` removed. Pause: `POST …/members/pause`. Colours 15 / 30 min.
     - Backend: the socket accepts only `ping`; `member.location`, `circle.event` and
       `member.paused` close it with BAD_DATA like any unknown type.
     - The socket stays, with its current ping, only to receive group events for notifications,
       held by the foreground service as today, until step 5. While still, the ping is late in
       Doze as today and the server may close the socket after 150 s; the app reconnects at the
       next fix (within 10 min) and a notification may be missed meanwhile. Accepted, since FCM
       replaces this in step 4.
4. **Push relay + FCM.**
   - New `push_relay/` service.
   - Backend: registration endpoint (token, registration id, AES key), payload encryption
     (`{r, iv, c}`), pushes for circle events, started/stopped sharing (Redis claim), removed /
     group deleted; registration deleted when the relay answers 404 or the member leaves.
   - App: Firebase, registration per member (id and Keystore-encrypted key in Room), decrypt and
     show notifications, clean up on removed / group deleted.
5. **Switch over.**
   - App: foreground service only while a group is shared.
   - Backend: group events go on the SSE stream; the WebSocket endpoint (`AGWSHandler`) is removed.
   - App: the SSE stream of the group on screen carries members, circles and group changes.
6. **Health check.** Hourly inexact alarm, server reachability, member validity, FCM
   re-registration, notification after repeated failures.

`CLAUDE.md` is updated in each step for what that step changes.

## SSE scope

Only the group shown on the map has an SSE stream open. Being removed from another group, or that
group being deleted, arrives by push. Other changes in groups not on screen (members, circles,
name) are picked up from the SSE snapshot when the group is opened.
