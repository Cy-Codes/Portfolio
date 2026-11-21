---
name: verify-change
description: >-
  Maps modified Portfolio files and modules to the minimum complete build, unit test,
  Konsist architecture fitness, and integration checks. Use after making code, script,
  schema, or config changes before finishing a task.
---
# Verify Portfolio Changes

Select the narrowest verification scope that fully covers your modified paths, then expand to full verification for cross-cutting or release changes.

## Path-to-Verification Mapping

| Modified Area | Minimum Required Command(s) |
| :--- | :--- |
| `core/` | `./scripts/verify.sh --fast` (or `--module core` for inner loop, then `--fast` before finish) |
| `server/server-db/` (Kotlin) | `./scripts/verify.sh --fast --module server-db` + Testcontainers if Docker is running |
| `server/server-db/resources/db/migrations/` or `db/` | `./scripts/up.sh deps --seed && ./scripts/smoke.sh deps` + `PostgresAndMinioIntegrationTest` |
| `server/server-public/` | `./scripts/verify.sh --fast --module server-public` |
| `server/server-admin/` | `./scripts/verify.sh --fast --module server-admin` |
| `app/presentation/` | `./scripts/verify.sh --fast --module presentation` |
| `app/composeUi/` or `app/*App/` | `./scripts/verify.sh --fast --module composeUi` (+ `./kotlin build` for `android`/`wasmJs`/`ios` changes) |
| `app/adminShared/` or `app/admin*App/` | `./scripts/verify.sh --fast --module adminShared` |
| `app/iosApp/` | `./kotlin test -m iosApp` and `./kotlin build` (unsandboxed host access for Xcode) |
| `scripts/*.sh` or `.agents/hooks*` | `./scripts/verify.sh --fast` (`bash -n`, JSON, `py_compile`, optional `shellcheck`) |
| `bruno/` or API contracts | `./scripts/up.sh local --seed && ./scripts/smoke.sh local --bruno` |

## Sandbox vs. Host Rules

1. **Sandboxed fast loop (`-p jvm`):** `./scripts/verify.sh --fast` and `./kotlin test -m <mod> -p jvm` run inside the sandbox cleanly.
2. **Unsandboxed host tools:** Full `./kotlin build` and `./kotlin test -m iosApp` invoke Xcode (`xcodeEnvironment`) and Android SDK lockfiles (`~/Library/Android/sdk/`), which require unsandboxed host execution.
3. **Docker tests:** `PostgresAndMinioIntegrationTest` and `./scripts/smoke.sh` require a running Docker daemon.

## Reporting Standard

- Run verification **after** the final edit.
- Report exact commands executed, exact pass/fail counts observed, and explicitly state any excluded or skipped suites (e.g., Testcontainers or iOS if not run).
