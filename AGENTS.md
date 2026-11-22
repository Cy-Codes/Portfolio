# Portfolio — Agent Working Guide

13-module Kotlin Multiplatform + Native SwiftUI + Ktor JVM + PostgreSQL 16 + Cloudflare R2/MinIO monorepo built with Kotlin Toolchain (`./kotlin`).

## Module Topology & Strict Boundaries

| Module | Role | Hard Boundary |
| :--- | :--- | :--- |
| `core` | `@Serializable` DTOs, `@Resource` routes, sealed `ApiError`, `QueryOptions`, `PortfolioJson` | Zero server, DB, or Compose imports |
| `app/presentation` | Decompose + FlowMVI + Ktor Client | **Zero Compose**, server, or Exposed imports |
| `app/composeUi` | Material 3 Adaptive UI + Markdown/Highlights + `DemoRegistry` | Zero admin, Ktor server, or Exposed imports |
| `app/androidApp`, `app/desktopApp`, `app/webApp` (`wasmJs`) | Public client entry points | Zero `codes.cy.portfolio.admin.*` imports |
| `app/iosApp` | Native SwiftUI visitor UI + inline Compose `demo:<id>` islands | Zero admin UI or `codes.cy.portfolio.admin.*` imports |
| `app/adminShared`, `app/adminDesktopApp`, `app/adminWebApp` | Isolated Admin CMS + analytics UI | Zero Ktor server or Exposed imports |
| `server/server-db` | Exposed 1.0 DSL, HikariCP, Flyway, `BlobStorage` | Zero Ktor server imports |
| `server/server-public` | Public read APIs, `POST /events`, `/read/{slug}`, SEO shell (`portfolio_public`) | Zero admin or Exposed imports |
| `server/server-admin` | Protected CMS CRUD, R2 presign/confirm, stats (`portfolio_admin`) | Zero direct Exposed imports (`*Repository` only) |

## Exact Verification Commands

- **Fast inner loop (JVM + Konsist + script syntax, sandbox-safe):**
  `./scripts/verify.sh --fast`
- **Single-module fast loop (runs module JVM tests + Konsist):**
  `./scripts/verify.sh --fast --module <module>` (or `./kotlin test -m <module> -p jvm`)
- **Konsist architecture fitness suite:**
  `./kotlin test -m server-db -p jvm --include-classes="*ArchitectureFitnessTest*"`
- **Full multiplatform build (JVM, Android, iOS, WasmJS):**
  `./kotlin build` *(Note: Android SDK lockfiles & Xcode require unsandboxed host execution)*
- **iOS simulator unit tests:**
  `./kotlin test -m iosApp` *(Requires unsandboxed Xcode access)*
- **Full Docker + Bruno E2E smoke:**
  `./scripts/up.sh local --seed && ./scripts/smoke.sh local --bruno`

## Non-Negotiable Rules

1. **Functional & Immutable:** Prefer `.map`/`.filter`/`.fold`/`.associate` and set-based SQL over mutable-accumulator loops. Domain models, DTOs, `QueryOptions`, `PagedResult<T>`, and FlowMVI `State`/`Intent`/`Action` must use `data class`/`sealed interface` with `val` properties and read-only `List`/`Map`/`Set`.
2. **CQS & Zero Magic Numbers:** Keep `GET` handlers 100% read-only (record analytics via `POST /api/v1/public/events`). Use `HttpStatusCode.*`, `QueryOptions.*`, enums, and `const val` / `readonly`.
3. **Immutable Migrations:** Committed `server/server-db/resources/db/migrations/V*.sql` files are strictly immutable—always append `V<n+1>__<snake_case>.sql`.
4. **Config, Secrets & Public-Repo IaC Hygiene:**
   - Keep `.env.example` and `infra/terraform/terraform.tfvars.example` synchronized whenever env vars or Terraform variables change.
   - Never create, edit, or commit real `.env` files, real `*.tfvars`, `*.tfstate*`, service account JSON keys, or plaintext secrets.
   - In `infra/terraform/`: create `google_secret_manager_secret` containers only (populate `secret_version` values out-of-band), parameterize all environment/prod identifiers (`project_id`, admin emails, state bucket), use additive `*_iam_member` bindings only (never `*_iam_policy`/`*_iam_binding`), and restrict CI Workload Identity Federation `attribute_condition` to the owning repo's `main` branch (never forked PRs).
5. **Verification & Honesty (Anti-Checkbox Theater):**
   - Never report a build or test as passing unless run **after** your final edit with observed output; state exact scope and exclusions.
   - Never check `[x]` in `docs/01_PLAN.md` for stubs, unimplemented flows, or unused config.
   - Never leave `error("unused")` test harness stubs for newly implemented routes.

## Deeper References (Read Only When Relevant)

- Full language & infrastructure rules: `CODING_STANDARDS.md`
- Architecture trade-offs (v1 `ADR 1`–`ADR 10`, v2 GCP/Terraform/CircleCI `ADR 11`–`ADR 14`): `docs/architecture-decisions.md` · `docs/architecture-decisions-v2.md`
- Phased roadmap & API/ERD contracts: `docs/01_PLAN.md`
- On-demand workspace runbooks in `.agents/skills/`: `coding-standards`, `verify-change`, `db-migration`, `add-api-endpoint`, `web-verify`, `release-preflight`
