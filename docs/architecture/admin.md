# Administration

[← Architecture](../architecture.md)

Whoever runs a server manages it through the admin API and the admin UI on top of it. Since names,
member names, positions and circles are encrypted on the phones, the admin sees what the server
sees: ids, counts, times, the admin flag. Anything added here must follow the same rule
([What the server learns](encryption.md#what-the-server-learns)).

## Admin API

Every endpoint is under `/api/v1/admin/**`, marked `@SecuredAdmin` and checked by
`AdminAuthInterceptor`: the `X-API-KEY` header must be the backend's `ADMIN_API_KEY`, compared in
constant time. The backend refuses to start without one. CORS allows only `ADMIN_UI_ORIGIN`.

| Endpoint | What it does |
|---|---|
| `GET /overview` | The server at a glance: version and served APIs, when it started, `REQUIRE_API_KEY`, the push relay, the position settings (kept for, saved every, deletion cron), whether the database and Redis answer, counts (groups, members, members registered for push, circles, groups without an admin, groups inactive for 30 days, saved positions, API keys), members online now, and the last run of `LocationSaveJob` and `LocationRetentionJob` with whether the next one is late. |
| `GET /anonymous-groups?page&size&q&sort&inactiveDays&noAdmin` | Groups with their counts (members, admins, online, push registered, circles) and last position. `q` is the start of a group's or one of its members' id; `sort` is `created` (default, newest first), `members` or `activity`; `inactiveDays` keeps the groups older than that with no position saved since; `noAdmin=true` the groups where nobody is an admin. |
| `GET /anonymous-groups/{id}` | One group, with the same counts. |
| `GET /anonymous-groups/{id}/members` | A group's members: joined, admin flag, online, push registered, last position. |
| `DELETE /anonymous-groups/{id}` | Deletes the group; every member gets `group.deleted` by push. |
| `DELETE /anonymous-groups/{id}/members/{memberId}` | Removes a member, who gets `member.left` by push. |
| `PATCH /anonymous-groups/{id}/members/{memberId}` | Sets the admin flag (`{isAGAdmin}`). |
| `GET /api-keys?page&size` | API keys, newest first, with their label, expiry and when each was last used (within 10 minutes). |
| `POST /api-keys` | Creates a key, with an optional `{label, expiresAt}` (label at most 60 characters); the answer is the only time the key is shown. |
| `PATCH /api-keys/{id}` | Replaces a key's label and expiry (`{label, expiresAt}`, null for none): to rename it, renew it or make it expire. |
| `DELETE /api-keys/{id}` | Revokes a key. |

Pages are 0-based and hold at most 100 items. Times are UTC without an offset (Java
`LocalDateTime`).

`AdminGroupService` reads the counts and saved times from the database in one query
(`AdminGroupRepository`, plain SQL: grouping, sorting and filtering are done there) and adds what
only Redis knows: who is online (`ag-online`, a position in the last 30 minutes, see
[Presence](sharing.md)) and the positions `LocationSaveJob` hasn't saved yet (`ag-location`, at most
2 minutes newer, see [Retention](groups-and-auth.md#retention)). The last position shown is the
newer of the two; sorting by activity and the inactivity filter use the saved one. All of it is
metadata the server already has, none of it new.

`AdminOverviewService` asks the database (one query of counts, `AdminOverviewRepository`) and Redis
(the `ag-online` keys counted with `SCAN`, and `job-run:{job}`) at the same time, and gives each 5
seconds: a store that is down can otherwise block a request for its whole timeout (a minute for
Redis). A store that fails or is late is reported down and what it knows is left out (null), the
rest is still answered. Each job keeps when it last ran and how many positions it saved or deleted
(`JobRunService`, written after its transaction commits, with no expiry), from whichever instance
ran it. The save job is late after three intervals with no run, the retention job an hour after the
time its cron (in the server's time zone) set for the next run; until a job first runs, counting
starts from when the backend started.

Every change made through the admin API is logged once it succeeds, at `INFO` with the `[Admin]`
prefix and only ids: a group deleted, a member removed or its admin flag set, an API key created,
updated (with its expiry) or revoked. A wrong admin key is logged at `WARN` with the method and
path, never the key. There is no other record of what the admin did: the server's logs are it.

The admin API is on the same port as the app's, so the proxy in front of the backend is where to
limit who reaches `/api/v1/admin/**`.

## Admin UI

`admin_ui/` is a React single-page app (MUI, TanStack Query, React Router, Vite), served by nginx in
the `lockate-admin-ui` image; `config.json`, filled at container start from `VITE_API_BASE_URL`,
tells it where the backend is. The admin signs in with `ADMIN_API_KEY`, kept in `sessionStorage`,
so it is gone with the tab; a 401 signs out and drops everything loaded.

- **Overview** (the home page): what is wrong first (database or Redis down, a late job, no push
  relay, API keys not required), then the counts, each linking to the list it counts (groups
  without an admin, inactive groups, API keys), and the server's version and settings. It is
  refreshed every minute.
- **Groups**: paged list with search by group or member id, sorting and filters for groups with
  no position for 7, 30 or 90 days and groups without an admin, all in the URL. Each row shows members (and how many are
  online), admins (a warning when there is none), circles, members registered for notifications
  and the last position. Rows can be selected to delete several groups at once, one request each,
  after typing how many.
- **A group's page**: the same counts, then its members with online or offline, whether their app
  registered for notifications, the admin switch and the last position. Taking the admin flag from
  the last admin, or removing the last admin, asks first: nobody in the group could then delete it
  or promote anyone.
- **API keys**: creating with a label and an expiry (never, in 30 or 90 days, in a year, or from
  the start of a chosen day in the admin's time zone), the key shown once in a dialog; editing the
  label and expiry; revoking. Expired keys are flagged, and the page says when the server doesn't
  require keys at all.
- Ids are shown by their first 8 characters, with the full id on hover and a copy button; times
  relative ("3 minutes ago") with the full local time on hover.
- Deleting a group, removing a member and revoking a key ask first and say what follows; deleting a
  group needs its short id typed. Deleting what is already gone counts as done. Results and errors
  show in a snackbar or in the dialog.
- Below 600 px the tables become lists of cards.
