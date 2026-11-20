# Lockate admin UI

The web console for the administrator of a Lockate server: it shows whether the server is healthy
and how it is set up, lists groups and their members, removes members, changes who is a group
admin, deletes groups and manages the API keys apps need when the server runs with
`REQUIRE_API_KEY=true`. It talks only to the backend's admin API
(`/api/v1/admin/**`, `X-API-KEY` = the backend's `ADMIN_API_KEY`), and since everything personal is
encrypted on the phones it sees only ids, counts and times. See
[the admin page of the architecture](../docs/architecture/admin.md).

React, MUI, TanStack Query and React Router, built with Vite.

## Running it

With the rest of the dev stack, from the repository root:

```bash
docker compose -f dev-docker-compose.yml up admin-ui
```

It is then on <http://localhost:3119>, talking to the backend on `localhost:3118`; sign in with the
backend's `ADMIN_API_KEY` (`change-me` in the dev compose).

On its own (Yarn 4 through Corepack):

```bash
corepack enable
yarn install
VITE_API_BASE_URL=http://localhost:3118 yarn dev
```

The backend accepts the admin UI only from `ADMIN_UI_ORIGIN` (CORS), by default
`http://localhost:5173`, Vite's port.

## Configuration

- In dev mode the backend URL is `VITE_API_BASE_URL`.
- The production image (nginx) serves `config.json`, filled at start from the container's
  `VITE_API_BASE_URL`, so one image works with any backend.

The admin key is kept in `sessionStorage`: it is gone when the tab is closed.

## Checks

```bash
make all   # format, check formatting, lint, type-check and test
make ci    # the same without formatting, as CI runs it
```
