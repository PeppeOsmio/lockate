# CLAUDE.md (admin_ui)

Build/test rules specific to this module. See the repo-root `CLAUDE.md` for the security invariants and the coding standards, `docs/architecture/admin.md` for the admin API and what the admin sees, and `README.md` here for running it.

## Commands

```bash
# Format (prettier), check formatting, lint (oxlint), type-check and test (vitest)
# (CI runs `make ci`: the same without formatting)
make all

# Dev server on http://localhost:5173, against a backend on :3118
VITE_API_BASE_URL=http://localhost:3118 yarn dev
```

Run these from `admin_ui/` (yarn).
