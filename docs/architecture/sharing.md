# Sharing

[← Architecture](../architecture.md)

A member is sharing while it sends positions. The server decides it from when members send, which
it already knows; nothing extra is sent for it.

## Timings

| Last position | Map dot | Group is told |
|---|---|---|
| ≤ 15 min | green | — |
| 15–30 min | yellow | — |
| > 30 min | grey | "stopped sharing" |

With a still fix every 10 min ([Sending positions](sending.md)):

- a fix on time keeps the dot green (at most 10 min old), and one up to 5 min late (Fused in Doze)
  still does;
- one lost fix (indoors, no network) turns it yellow at 15 min, and the next, at 20, green again;
- grey and "stopped sharing" at 30 min take about two lost fixes in a row, rare enough not to raise
  false alarms;
- a dead phone is reported after 30 min. Stopping on purpose is reported at once (`members/pause`).

The colours are computed on the phone (`LastSeenText.kt`, `LIVE_WINDOW`, `RECENT_WINDOW`). The
member's marker on the map follows the same rule: opaque while the dot is green, faded once it
isn't.

## The timer in Redis

`AGPresenceService`, two keys per member:

| Key | TTL | Value |
|---|---|---|
| `ag-presence:{groupId}:{memberId}` | 30 min | the timer |
| `ag-online:{groupId}:{memberId}` | 31 min | last seen; "announced as sharing" |

- Every position refreshes `ag-presence`, then sets `ag-online` with `SET … GET`. A nil previous
  value means this position is the one that started sharing: it announces `online: true`.
- Every backend instance hears the expiry of `ag-presence` (the backend turns on
  `notify-keyspace-events Ex` at startup), but "stopped sharing" must be announced once: a Lua
  script `GETDEL`s `ag-online`, only if `ag-presence` wasn't recreated meanwhile, and only the
  instance that got the value announces `online: false`.
- `members/pause` does the same at once. Leaving or being removed deletes both keys silently.
- A lost expiry event (Redis restart) is accepted: `ag-online` expires by itself a minute later, so
  nothing stays stuck; at worst "stopped sharing" isn't announced.

The announcement is a `member.presence` frame (`{memberId, online, lastSeen}`), published to the
group ([Real-time](realtime.md)) and pushed to the other members ([Push notifications](push.md)).
The app shows it in the member's notification like a circle event and doesn't store it.
