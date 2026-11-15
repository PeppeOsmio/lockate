# Health check

[← Architecture](../architecture.md)

Since the switch-over nothing in the background talks to a server unless we share with one of its
groups, and notifications come only by push. A server that went away, a group we were removed
from, or a push registration that broke (a new FCM token whose `onNewToken` was missed) would go
unnoticed until the app is opened. The health check notices them.

## What it does

`HealthCheck`, run by `HealthCheckWorker` about every hour, for each server with a group we are
still in:

1. **Does it answer, and does it speak our API?** `GET /api/version`
   ([Versioning](../versioning.md)), which has no API version in its path, so any server answers it.
   - No answer (no connection, timeout, 5xx): the server's count of runs in a row without an answer
     goes up, and at **3** (`UNREACHABLE_RUNS_TO_NOTIFY`, about 3 hours) a notification "Can't
     reach *server*" is shown, once: the count keeps going up without notifying again until the
     server answers. Nothing else is checked on it.
   - Any answer: the count goes back to 0 and that notification, if any, is cleared.
   - The server doesn't serve the app's API (`APP_API`): a notification "*server* needs an update"
     (no `/api/version`, or only older majors) or "Update Lockate to use *server*" (only newer
     majors) is shown once, and its groups aren't checked: answers to an API it doesn't speak mean
     nothing, and a 404 would mark them deleted. When it speaks our API again the notification is
     cleared.
2. **Each group of a compatible server:** `GET …/members/auth/verify`
   (`AnonymousGroupService.verifyMemberAuth`). A 401/403 or 404 means we were removed or the group
   was deleted, and the group is marked as such, as a push or the stream would do.
3. **Pushes:** every group is registered again (`PushRegistrationService.registerAll`), which
   replaces each registration with the current FCM token. A token change missed by `onNewToken` is
   fixed within the hour.

With no group it does nothing. The state is kept in DataStore per server
(`health_failures_{connectionId}`, `health_incompatible_{connectionId}`), so it survives the
process; each notification has its own tag per server on the "Server problems" channel.

The server list (Manage connections) shows what each server's `GET /api/version` says: "Server
1.2.0", or what needs an update.

## Scheduling

WorkManager, a periodic work of 1 hour (`enqueueUniquePeriodicWork`, `KEEP`, scheduled at every app
start), with a network constraint: without a network no server can be reached anyway, so a day
offline doesn't count against any server, and the run waits for the network to come back.
WorkManager keeps the work across reboots and fits it into Doze's maintenance windows, so "about
every hour" can stretch while the phone sleeps; that is fine for a check that only needs to catch
problems lasting hours.

WorkManager was dropped for the location heartbeat ([Sending positions](sending.md)) because its
periodic work can't run more often than every 15 minutes and is deferred by design; an hourly check
is what it's for. An exact alarm would need the "Alarms & reminders" permission.

## Why 3

One failed run is often just a phone switching networks or a server being redeployed; three in a
row (about 3 hours) is a real outage, without waiting so long that the user has already noticed
silent groups by themselves. It is one constant.
