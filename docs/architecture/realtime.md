# Real-time

[← Architecture](../architecture.md)

How what happens in a group reaches the phones that are watching. Phones that aren't watching get
pushes instead ([Push notifications](push.md)).

## Frames

The server speaks in JSON frames with a `type` (`AGFrameDto`), the same on every transport:

| Frame | |
|---|---|
| `snapshot` | Members with their last saved record, and circles. First on every stream, and in the join response. |
| `member.joined` / `left` / `updated` | Membership changes. |
| `circle.upserted` / `deleted` | [Circles](circles.md). |
| `circle.event` | Another member's circle event, with the server's timestamp, never stored. |
| `member.presence` | `{memberId, online, lastSeen}`: [Sharing](sharing.md). |
| `location` (SSE only) | A member's position (`MemberLocationDto`). |

## SSE stream: the group on screen

`GET /api/v1/anonymous-groups/{id}/locations` (`AGLocationStreams`), opened only while the map
shows that group: a `snapshot`, then a `location` per position, comment heartbeats every 15 s from
one shared scheduler. It ends when the member is removed or the group deleted.

Only the group on screen has a stream. Being removed from another group, or that group being
deleted, arrives by push; other changes (members, circles, name) come with the snapshot when the
group is opened.

On the phone, `holdLocationStream` saves positions to Room before the map gets them. Every
snapshot (socket, SSE, join) is decrypted and saved by the same code, under a per-group lock.

## Group socket (until the switch-over)

`/api/v1/ws/anonymous-groups/{id}` (`AGWSHandler`), one per member and group, held by the
foreground service. It carries every frame above except `location`, and is used only to receive:
the client sends only `{"type":"ping"}`, anything else closes it with BAD_DATA.

- Liveness: the client pings all its sockets on `PingClock`'s tick (the wall-clock minute plus a
  jitter fixed per app run), the server answers `pong`, the client reconnects when none comes within
  10 s. The server closes sockets silent for 150 s (`SESSION_NOT_RELIABLE`). Reconnects back off up
  to 60 s.
- Close codes: 4401 the member was removed, 4404 the group was deleted.
- It notifies only for groups whose server doesn't push ([Push notifications](push.md)).

The switch-over ([Roadmap](../roadmap.md)) moves group events onto the SSE stream and removes the
socket: in Doze its pings come late anyway, and push already covers what it was for.

## Across backend instances

State changes and circle events are published on the Redis channel `ag-{groupId}-events` (after
the transaction commits), positions on `ag-{groupId}-locations`. Every socket subscribes to the
first, every SSE stream to both, so any instance can serve any member. Each instance delivers in
order: one listener thread, and a `Connection` per socket / `Stream` per SSE stream that holds back
what arrives while its snapshot is built. Nothing is decrypted on the way.
