# Push notifications

[← Architecture](../architecture.md)

Pushes reach a phone whose app isn't running: circle events, sharing changes, being removed from a
group, a group being deleted. They go backend → [push relay](push-relay.md) → FCM → phone, and only
the backend and the app can read them.

## Registration

The app has one FCM token per phone (one Firebase project), the same for every server and group,
so the token can't tell the app which group a push is for. A registration id does.

- Per member (one group on one server) the app generates a 16-byte registration id `r` and a
  32-byte AES key, kept in Room (`push_registration`, the key encrypted with the Keystore;
  `PushRegistrationService`).
- It registers `PUT /api/v1/anonymous-groups/{id}/members/push-registration`
  `{fcmToken, registrationId, key}` (Base64): at app start for every group, for each new group, and
  on `onNewToken`. A wrong size is 400 `invalid_push_registration`.
- The server stores it on `ag_member` (`push_token`, `push_registration_id`, `push_key`): one per
  member, keyed by the authenticated member, replaced by the next; deleted with the member.
- `r` only has to be unique on the phone, where it finds the Room row; two phones with the same `r`
  don't interfere.
- The answer `{pushes: true|false}` says whether the server has a relay. The app stops notifying
  from the socket only for groups pushed, so nothing is told twice and a server without a relay
  works as before.

The push key is stored in clear on the server, since the server seals with it: a database leak
lets someone holding the FCM token forge notifications for that member, as a leak already exposes
every ciphertext and token hash.

## Payload

An FCM `data` message, high priority:

```
{ "r": registrationId, "iv": 12 bytes, "c": AES-GCM(pushKey, frame, aad = "lockate-push-v1|" + r) }
```

- `frame` is the same `AGFrameDto` JSON the sockets get ([Real-time](realtime.md)), so the app
  parses it with code it already has; a circle event inside is still encrypted with the group key.
  A few hundred bytes, under FCM's ~4 KB, so nothing is stored for later fetch.
- `r` in the AAD stops a payload from being replayed under another registration.
- AES-GCM authenticates too: neither the relay nor someone with a stolen token can make the app show
  anything.

## What is pushed

`AGPushService` / `AGPushSender`, after the transaction commits, on virtual threads, sealed per
member:

| Event | To |
|---|---|
| `circle.event` | the group's other members |
| `member.presence` (started / stopped sharing) | the group's other members, from the one instance that announces it |
| `member.left` with the member's own id | a member removed by the server admin, read before the row goes |
| `group.deleted` | every member but the one who deleted it, read before the delete |

A 404 from the relay (FCM no longer knows the token) clears the registration, if it is still the
same one. The relay URL is `lockate.push.relay-url` (`PUSH_RELAY_URL`); empty pushes nothing, and
without a relay, or with one unreachable, everything works except notifications.

## On the phone

`LockateMessagingService.onMessageReceived` → `AnonymousGroupService.openPush`: look up `r` in Room
(unknown: left group, old registration or forged → dropped), open it (fails → dropped), then show a
circle event or sharing change through `CircleNotifier` ([Circles](circles.md#notifications)), or
mark the group removed / deleted as for the socket's 4401 / 4404.

### Members and circles the phone doesn't know yet

A push names a member and, for a circle event, a circle; the notification needs their names, which
the phone has only once it has saved them. While the foreground service holds every group's socket,
`member.joined` and `circle.upserted` are saved as they happen, but a push can still win the race: a
new member who starts sharing at once, or a circle crossed right after it was created, or a socket
that is down. After the switch-over ([Roadmap](../roadmap.md)) the phone learns about new members
and circles only when it opens the group, so a miss becomes the normal case.

So when a push names a member, or for a circle event a circle, missing from Room (`namesUnknown`),
`openPush` fetches the group's snapshot with `GET /api/v1/anonymous-groups/{id}/snapshot` (the same
snapshot the streams start with), saves it through the usual snapshot code under the group's lock,
and then describes the push. A member or circle still unknown after that (removed meanwhile), or a
server that can't be reached, means the push is dropped. A high-priority push
gives the app a few seconds of work, and a miss costs one request. Only on a miss: pushing every
group change too would cost a push per change and still leave a gap whenever one is lost.

Groups need nothing of the kind: `r` names the group on the phone, and an unknown `r` is a group the
phone has left or never had. A group's name never changes.

Firebase is set up by hand (`FirebaseSetup`) from four public values per app flavor
([Environments](environments.md)), not from `google-services.json`; without them the app has no
token and registers nothing.

## Limits

- No notifications on phones without Google Play services, and no F-Droid build (it rejects
  Firebase). UnifiedPush could carry the same encrypted payload ([Roadmap](../roadmap.md)).
