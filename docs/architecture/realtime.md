# Real-time

[← Architecture](../architecture.md)

How what happens in a group reaches the phones that are looking at it. Phones that aren't get
pushes instead ([Push notifications](push.md)); a phone that shares in the background learns of
changed places from its own posts ([Sending positions](sending.md#keeping-the-places-current)).

## Frames

The server speaks in JSON frames with a `type` (`AGFrameDto`), the same on the stream and in
pushes:

| Frame | |
|---|---|
| `snapshot` | Members with their last position, and places. First on every stream, in the join response, and from `GET …/snapshot`. |
| `member.joined` / `left` / `updated` | Membership changes. A `member.left` with our own id is our removal. |
| `place.upserted` / `deleted` | [Places](places.md). |
| `place.event` | Another member's place event, with the server's timestamp, never stored. |
| `member.presence` | `{memberId, online, lastSeen}`: [Sharing](sharing.md). |
| `group.deleted` | The group is gone. |

## SSE stream: the group on screen

`GET /api/v1/anonymous-groups/{id}/locations` (`AGStreams`), opened only while a screen shows that
group (the map, the places):

1. a `snapshot` event;
2. then, as they happen, a `location` event per position (`MemberLocationDto`) and a `frame` event
   per frame above, except our own place events and sharing changes;
3. comment heartbeats every 15 s, from one shared scheduler.

What arrives while the snapshot is being built is held back and sent after it, in order. The stream
ends right after the `member.left` of our removal or the `group.deleted` frame, which the phone
applies (the group is marked removed or deleted) before it sees the end, so it doesn't reconnect.
A 401 or 404 when connecting marks the group the same way. Reconnects back off up to 60 s, or wait
for the network.

On the phone, `holdGroupStream` applies every frame to Room under a per-group lock and emits it on
`groupFrames`; positions are saved to Room before the map gets them. Every snapshot (stream, join,
`GET …/snapshot`) is decrypted and saved by the same code.

Only the group on screen has a stream. Being removed from another group, or that group being
deleted, arrives by push; other changes (members, places) come with the snapshot when the group is
opened, or when a push or a location post needs them.

**Why no socket.** Until the switch-over each phone held one WebSocket per group, in the foreground
service, to receive the group's events in the background. In Doze its pings came late, so it kept
dropping and reconnecting; it kept a location foreground service running for groups we don't even
share with (a Play policy risk); and FCM pushes already wake the phone for what it was for. A server
without a push relay therefore sends no notifications at all.

## Across backend instances

State changes and place events are published on the Redis channel `ag-{groupId}-events` (after
the transaction commits), positions on `ag-{groupId}-locations`. Every SSE stream subscribes to
both, so any instance can serve any member. Each instance delivers in order: one listener thread,
and a `Stream` per SSE stream that holds back what arrives while its snapshot is built. Nothing is
decrypted on the way.
