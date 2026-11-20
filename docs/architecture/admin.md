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
| `GET /anonymous-groups?page&size` | Groups, newest first, with member count and last saved position. |
| `GET /anonymous-groups/{id}/members` | A group's members: joined, admin flag, last saved position. |
| `DELETE /anonymous-groups/{id}` | Deletes the group; every member gets `group.deleted` by push. |
| `DELETE /anonymous-groups/{id}/members/{memberId}` | Removes a member, who gets `member.left` by push. |
| `PATCH /anonymous-groups/{id}/members/{memberId}` | Sets the admin flag (`{isAGAdmin}`). |
| `GET /api-keys?page&size` | API keys, newest first, with when each was last used. |
| `POST /api-keys` | Creates a key; the answer is the only time it is shown. |
| `DELETE /api-keys/{id}` | Revokes a key. |

Pages are 0-based and hold at most 100 items. Times are UTC without an offset (Java
`LocalDateTime`). "Last saved position" is the database's, which `LocationSaveJob` writes every 2
minutes ([Retention](groups-and-auth.md#retention)).

The admin API is on the same port as the app's, so the proxy in front of the backend is where to
limit who reaches `/api/v1/admin/**`.

## Admin UI

`admin_ui/` is a React single-page app (MUI, TanStack Query, React Router, Vite), served by nginx in
the `lockate-admin-ui` image; `config.json`, filled at container start from `VITE_API_BASE_URL`,
tells it where the backend is. The admin signs in with `ADMIN_API_KEY`, kept in `sessionStorage`,
so it is gone with the tab; a 401 signs out and drops everything loaded.

- **Groups**: paged list (page and size in the URL), each group's members on its page, removing
  members and switching the admin flag.
- **API keys**: creating, shown once in a dialog, and revoking.
- Ids are shown by their first 8 characters, with the full id on hover and a copy button; times
  relative ("3 minutes ago") with the full local time on hover.
- Deleting a group, removing a member and revoking a key ask first and say what follows; deleting a
  group needs its short id typed. Deleting what is already gone counts as done. Results and errors
  show in a snackbar or in the dialog.
- Below 600 px the tables become lists of cards.
