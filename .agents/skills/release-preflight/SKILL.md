---
name: release-preflight
description: >-
  Runs the full pre-merge and release readiness checklist across multiplatform builds,
  Testcontainers, iOS tests, Bruno E2E smoke tests, .env.example parity, and plan
  accuracy. Use before cutting a release or signing off on a major milestone.
---
# Release & Milestone Preflight Checklist

Do not declare a milestone complete until every applicable gate below has actually passed after the final edit.

## 1. Full Multiplatform Build & Tests

Run the full verification suite (requires unsandboxed Xcode/Android SDK access and a running Docker daemon):
```bash
./scripts/verify.sh --full
```
This runs:
- Shell (`bash -n`, optional `shellcheck`), `hooks.json` schema, and Python hook compilation
- Full `./kotlin build` (JVM, Android, iOS, WasmJS)
- All 7 JVM module unit suites + Konsist `ArchitectureFitnessTest`
- `./kotlin test -m iosApp` on the iOS simulator
- `PostgresAndMinioIntegrationTest` via Testcontainers

## 2. Local Docker + Bruno End-to-End Smoke

```bash
./scripts/up.sh local --seed
./scripts/smoke.sh local --bruno
```
Confirm all `00-Health` through `05-Negative-Tests` Bruno assertions pass (`400`, `401`, `403`, `404`, `409`) and `01_schema_structure_tests.sql` reports 0 failures.

## 3. Configuration & Security Parity Check

- Compare every `System::getenv` / `env("...")` call in `AdminServerConfig.kt`, `PublicServerConfig.kt`, `DatabaseConfig.kt`, and `compose.yaml` against `.env.example`—verify every variable is documented and no real secrets are committed.
- Confirm production defaults in `*ServerConfig.kt` fail closed (`APP_ENV=prod` rejects `dev-token.sh`).

## 4. Spec-to-Code Honesty Check

- Review `docs/01_PLAN.md` and `docs/architecture-decisions.md` against the diff:
  - Never mark a task `[x]` if only config placeholders, empty tables, or stubs exist.
  - Ensure `git status` is clean of temporary files, `.env` secrets, or unintended build artifacts.
