# Agent instructions — carticom-backend

# Git workflow (effective 2026-10-08)

- NEVER push to `main`. All work is committed to a feature branch (or `dev`) and pushed to `origin/<branch>` only.
- The repository owner merges into `main` manually — do not merge, fast-forward, or force-push `main` yourself.
- Run `.\mvnw.cmd test` before pushing so `main` stays deployable when the owner merges.

# Build commands (Windows/PowerShell)

- `npm.ps1`/`npx.ps1` are blocked — call `node` directly (e.g. `node node_modules\typescript\bin\tsc --noEmit`).
- Backend tests: `.\mvnw.cmd test`; production jar: `.\mvnw.cmd package`.
