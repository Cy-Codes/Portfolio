# Portfolio — Master Coding Standards & Architectural Rules

This document defines the authoritative coding standards, architectural boundaries, and verification rules across the **Portfolio** workspace (Kotlin Multiplatform, Compose Multiplatform, SwiftUI, Ktor JVM, Exposed 1.0, PostgreSQL 16, Flyway, Docker, and Bruno).

The architectural rationale and trade-offs behind these rules are documented in [docs/architecture-decisions.md](docs/architecture-decisions.md), and the phased implementation roadmap is tracked in [docs/01_PLAN.md](docs/01_PLAN.md).

---

## Part I: Universal Coding Standards (All Languages & Layers)

```text
+-------------------------------------------------------------------------+
|                       Universal Standards Pillars                       |
+--------------------+--------------------+-------------------------------+
| Functional First   | Immutable Always   | Explicit Side Effects (CQS)   |
| Maps over Loops    | val & data class   | Functional Core / Imp. Shell  |
+--------------------+--------------------+-------------------------------+
| Helpers & Ext. Fns | Zero Magic Numbers | Anti-Pattern Zero Tolerance   |
+--------------------+--------------------+-------------------------------+
```

### 1. Functional Logic Over Imperative Loops

All Kotlin, Swift, SQL, and shell code must prefer declarative, functional transformations over state-mutating imperative loops.

1. **Choose Transformations Over Imperative Loops:**
   - **Kotlin:** Use collection and sequence pipelines (`.map { }`, `.filter { }`, `.fold(...)`, `.associate { }`, `.flatMap { }`, `generateSequence`). Never allocate `mutableListOf()` or `mutableMapOf()`, iterate input, and imperatively call `.add()` or `put()`.
   - **Swift:** Use `.map`, `.filter`, `.reduce`, and `.compactMap` over mutable accumulator arrays.
   - **PostgreSQL:** Use set-based SQL (`generate_series`, CTEs, window functions, `jsonb_agg`, `jsonb_build_object`) over procedural `FOR ... LOOP` blocks or cursors.
   - **Bash:** Use `jq` and `awk` pipelines for structured data transformation.
2. **Permitted Loop Exceptions (Must Be Commented):**
   - Loops (`for`, `while`) are allowed **only** for:
     - Streaming unbuffered byte buffers or network/DB streams (`ResultSet.next()` wrapped in `generateSequence`, Ktor `ByteReadChannel` chunking).
     - Benchmark-proven hot paths where closure allocations cause measured GC pressure.
     - Chunked DB maintenance deleting/updating batches in bounded windows to avoid lock escalation.
     - Bounded Bash polling/retry probes (`wait_for_health`) or iterating fixed `readonly` arrays in helper functions.
   - **Mandatory rule:** Every exception loop must carry an inline comment explaining why a functional construct was rejected.

```kotlin
// ❌ BAD: Imperative loop with mutable accumulator
val publishedSlugs = mutableListOf<String>()
for (writeup in writeups) {
    if (writeup.status == WriteupStatus.PUBLISHED) {
        publishedSlugs.add(writeup.slug)
    }
}

// ✅ GOOD: Declarative functional pipeline
val publishedSlugs: List<String> = writeups
    .filter { it.status == WriteupStatus.PUBLISHED }
    .map { it.slug }
```

### 2. Immutability by Default

1. **Kotlin Contracts & Models:**
   - Define all domain models, DTOs, `QueryOptions`, `PagedResult<T>`, and FlowMVI `State`/`Intent`/`Action` types as `data class`, `sealed interface`, or `data object` with `val` properties only.
   - Expose read-only Kotlin collection interfaces (`List<T>`, `Map<K, V>`, `Set<T>`) everywhere. Never expose `MutableList<T>` or `MutableMap<K, V>` in domain models, MVI state, or public return types.
   - Mutable `var` properties are forbidden on domain models, DTOs, repositories, services, and routes.
2. **Pure Functions:**
   - Functions must compute new values from inputs without mutating arguments or hidden global state.
3. **Database & Artifact Immutability:**
   - Merged Flyway migration files (`migrations/V<n>__*.sql`) are **strictly immutable**. Never edit, reorder, or delete an applied migration; evolve schemas strictly by appending `V<n+1>__<description>.sql`.
   - Bash constants must be declared `readonly`.
   - Container images must be tagged by immutable content/git hashes — never deploy `:latest` application images.

### 3. Explicit Demarcation of Side Effects (CQS & Functional Core / Imperative Shell)

1. **Command-Query Separation (CQS):**
   - **Queries (Pure / Read-Only):** Idempotent, side-effect free, safe to retry (`get*`, `find*`, `parse*`, `calculate*`, `to*`, `apply*`, `*QueryService`).
   - **Commands (Side-Effecting):** Explicitly mutate state, perform database writes, execute uploads, or emit telemetry (`execute*`, `create*`, `update*`, `delete*`, `publish*`, `upload*`, `record*`, `*CommandService`).
2. **Strict Read-Only `GET` Semantics:**
   - HTTP `GET` handlers (including `GET /api/v1/public/documents/{id}/download` and `GET /read/{slug}`) must remain **100% read-only and idempotent** with zero database writes.
   - Telemetry such as page views, writeup reads, and document downloads must be emitted explicitly via `POST /api/v1/public/events`.
3. **Functional Core, Imperative Shell:**
   - Keep pagination normalization, query validation, `ApiError` mapping, Markdown/SEO metadata building, and FlowMVI reducers pure with zero I/O dependencies.
   - Isolate database transactions (`newSuspendedTransaction`), JDBC/Exposed I/O, Cloudflare R2 calls, and HTTP transport inside explicit `infrastructure` and `api` boundary classes.
   - Always close `AutoCloseable` I/O resources deterministically using Kotlin's `.use { ... }`.
   - In Bash scripts, label functions with `# Pure query: ...` or `# Side effect: ...`.

### 4. Zero Magic Numbers & String Literals

1. **Ban Unnamed Literals:** Raw HTTP status numbers (`404`, `409`, `500`), pagination sizes (`20`, `100`), port numbers (`5432`, `8080`, `9000`), timeouts, retry counts, and string discriminators are prohibited in business logic.
2. **Required Replacements:**
   - Use Ktor `HttpStatusCode.*` constants (`HttpStatusCode.OK`, `HttpStatusCode.Created`, `HttpStatusCode.NotFound`, `HttpStatusCode.Conflict`).
   - Centralize pagination bounds in `QueryOptions` (`DEFAULT_PAGE = 1`, `MIN_PAGE_SIZE = 1`, `DEFAULT_PAGE_SIZE = 20`, `MAX_PAGE_SIZE = 100`).
   - Model bounded concepts with strongly typed `enum class` types (`WriteupStatus`, `DocumentCategory`, `AnalyticsEventType`, `ViewportTier`, `ClientPlatform`) rather than raw strings.
   - Use `const val` for Kotlin constants, `CHECK` constraints / SQL variables in Postgres, and `readonly` constants in Bash.

### 5. Eliminate Redundancy via Helper & Extension Functions

1. **Pure Top-Level Kotlin Extensions:** Consolidate repeated mapping, validation, pagination, and response-handling code into focused extension files rather than duplicating logic or building deep inheritance hierarchies:
   - `QueryOptionsExtensions.kt` (`Parameters.toQueryOptions(...)`, `QueryOptions.toSqlOffset()`)
   - `CallResultExtensions.kt` (`ApplicationCall.respondApiResult(...)`, `ApplicationCall.respondCreated(...)`)
   - `ApiErrorExtensions.kt` (`ApiError.toHttpStatusCode()`, `Throwable.toApiError(isDev)`)
   - `IdentifierValidationExtensions.kt` (`String.isSafeSlug()`, `String.isSafeSqlIdentifier()`)
2. **Shared Shell Workflows:** Repeated Docker, `psql`, Bruno, and health-probe commands must live once in `scripts/lib.sh` and be `source`d — never duplicated across scripts.

---

## Part II: Kotlin Multiplatform, Presentation & UI Standards

### 6. Toolchain & Module Topology

- **Build System:** [Kotlin Toolchain](https://kotlin-toolchain.org/) `0.13.0` (`project.yaml`, `module.yaml`, `libs.versions.toml`). Prefer native Toolchain settings (`compose`, `ktor`, `ksp`, `serialization`) over bespoke build scripts.
- **Target Platforms:**
  - `//core`: `kmp/lib` (`jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `wasmJs`)
  - `//app/presentation`: `kmp/lib` (`jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `wasmJs`) — **zero Compose dependencies**
  - `//app/composeUi`: `kmp/lib` (`jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `wasmJs`)
  - `//app/androidApp`, `//app/desktopApp`, `//app/webApp` (`wasm-js/app`), `//app/iosApp` (native SwiftUI)
  - `//app/adminShared` (`jvm`, `wasmJs`), `//app/adminDesktopApp`, `//app/adminWebApp` (`wasm-js/app`)

### 7. Shared `//core` Contracts & `kotlinx.serialization`

- **Type-Safe API Contracts:** Define `@Serializable` DTOs and [Ktor `@Resource` routes](https://ktor.io/docs/type-safe-routing.html) inside `//core` so servers and Ktor Client share one compile-time API contract.
- **Compile-Time Serialization Only:** Never use reflection-based JSON serializers. Always use JetBrains `kotlinx.serialization` (`@Serializable`) with one shared frozen `Json` configuration exported from `//core`:
  ```kotlin
  val PortfolioJson: Json = Json {
      encodeDefaults = true
      ignoreUnknownKeys = true
      explicitNulls = true
  }
  ```
- **Custom Serializers:** Shared scalar/UUID/timestamp wrappers must live in one `//core` serializer registry (`@file:UseSerializers(...)`).
- **Boolean Naming:** Preserve `is*` property names (`isFeatured`, `isPublic`, `isPublished`) cleanly via `kotlinx.serialization` without property-name mangling.

### 8. Pure KMP Error Contract (`ApiError`)

Rather than an external media-type envelope, all client-server error contracts in `//core` use a **pure KMP `@Serializable sealed interface ApiError`**:

- Closed hierarchy with explicit `@SerialName` discriminators:
  - `ApiError.ValidationError(val message: String, val fieldErrors: List<FieldViolation>)` → `400 Bad Request`
  - `ApiError.Unauthorized(val message: String)` → `401 Unauthorized`
  - `ApiError.Forbidden(val message: String)` → `403 Forbidden`
  - `ApiError.NotFound(val message: String, val resource: String, val identifier: String)` → `404 Not Found`
  - `ApiError.Conflict(val message: String, val constraint: String? = null)` → `409 Conflict`
  - `ApiError.InternalError(val message: String, val traceId: String? = null)` → `500 Internal Server Error`
- Server `StatusPages` maps domain exceptions, validation failures, overflow errors, missing routes, and PostgreSQL integrity violations cleanly via a pure `Throwable.toApiError(isDevelopment: Boolean)` helper.
- **Zero Information Disclosure:** Never expose raw exception messages, SQL state, hostnames, or stack traces in `ApiError.InternalError` when outside `dev` / `development` environments; return a generic sanitized message plus `traceId`.
- FlowMVI stores switch exhaustively over `ApiError` via `when (error)` with no catch-all surprises.

### 9. Pure KMP Presentation (`//app/presentation` — Decompose + FlowMVI)

- **Strict Compose-Free Boundary:** `//app/presentation` must contain **zero** `androidx.compose.*` or `org.jetbrains.compose.*` imports. Enforce this automatically with [Konsist](https://docs.konsist.lemonappdev.com/) architecture tests.
- **Navigation ([Decompose](https://arkivanov.github.io/Decompose/component/overview/)):**
  - Components own `ComponentContext`, `ChildStack`, and `ChildPanels` (for adaptive list-detail / supporting panes).
  - Navigation flows strictly parent-driven: child components emit sealed `Output` intents; the root/parent component mutates `StackNavigation` or `PanelsNavigation`.
  - Web URLs and browser Back/Forward history synchronize through Decompose's `WebHistoryController` on `wasmJs`.
- **State Management ([FlowMVI](https://respawn.pro/FlowMVI/)):**
  - Every screen/feature uses immutable `MVIState`, sealed `MVIIntent`, and sealed `MVIAction` types wired through FlowMVI's Essenty/Decompose lifecycle integration.
  - Cross-cutting concerns (logging, analytics, error recovery) are installed via FlowMVI plugins rather than duplicated inside individual stores.

### 10. Public Compose UI (`//app/composeUi`) & Native SwiftUI (`//app/iosApp`)

- **Adaptive Breakpoints & Input Modality (`//app/composeUi`):**
  - Drive layouts exclusively from window size in `dp` via `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` ([Material 3 Breakpoints](https://m3.material.io/foundations/layout/applying-layout/window-size-classes)), never physical screen inches or OS checks:
    - `Compact` (`< 600dp`): single pane, bottom navigation bar, thumb-friendly actions; check compact height (`< 480dp`) for landscape phones.
    - `Medium` (`600–839dp`) & `Expanded` (`840–1199dp`): navigation rail, multi-column grids, two-pane `ChildPanels` at `>= 840dp`.
    - `Large` (`1200–1599dp`) & `Extra-Large` (`>= 1600dp`): persistent desktop/web navigation chrome, optional supporting pane (Table of Contents / metadata), capped reading measure (`~720–840dp`) for long writeups.
  - Layer input-modality polish (hover highlights, keyboard shortcuts, visible desktop scrollbars) separately from layout breakpoints.
- **Markdown & Embedded Demos:**
  - Render writeups in Compose using [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) + its `-code` artifact ([Highlights](https://github.com/SnipMeDev/Highlights)).
  - Map fenced `demo:<id>` blocks strictly to a compile-time `DemoRegistry` (allowlisted IDs only; unknown demo IDs render an explicit fallback card rather than crashing).
- **Web Target (`//app/webApp`):**
  - Target **`wasmJs`** exclusively (not legacy Compose JS). Pair with Ktor dynamic SEO injection and Brotli/Gzip compression.
- **Native iOS (`//app/iosApp`):**
  - Build visitor screens in native **SwiftUI** (`TabView`, `NavigationSplitView`, native Swift Markdown + syntax highlighting) bound directly to `//app/presentation` Decompose state via `@StateValue` / `StackView`.
  - When a writeup references an interactive `demo:<id>`, embed only that single Compose demo widget inline via `ComposeUIViewController` + `UIViewControllerRepresentable` ([SwiftUI Integration](https://kotlinlang.org/docs/multiplatform/swiftui-integration.html)).
- **Dedicated Admin UI (`//app/adminShared`, `//app/adminDesktopApp`, `//app/adminWebApp`):**
  - Keep all admin screens (`Desktop` + `wasmJs` Web) outside `//app/composeUi`, `//app/webApp`, and `//app/iosApp` so public visitors download zero admin code and iOS requires zero admin SwiftUI screens.
  - Admin writeup editor reuses `//app/composeUi` Markdown + `DemoRegistry` components for pixel-accurate live preview.

---

## Part III: Ktor JVM Server Standards (Compile-Time Public/Admin Split)

### 11. Public/Admin Module Isolation & Clean Architecture Layering

Keep the backend split at compile time across three server modules:
- **`server/server-db` (`jvm/lib`):** Exposed 1.0 DSL tables, `ResultRow` mappers, repositories, HikariCP factory, `BlobStorage` abstraction (Cloudflare R2 / MinIO), Flyway runner helpers. **Never** imports Ktor routing or auth plugins.
- **`server/server-public` (`jvm/app`):** Public portfolio read APIs, `POST /api/v1/public/events`, `/read/{slug}` HTML Reader View, dynamic Wasm SEO shell, `/sitemap.xml`, `/robots.txt`, `/health`, `/health/ready`. Connects with **`portfolio_public`** DB role and holds **zero** R2 write credentials or admin routes.
- **`server/server-admin` (`jvm/app`):** Admin CRUD endpoints, R2 presigned upload & verify routes, analytics overview queries, OAuth allowlist + Cloudflare Access JWT auth, `/health`, `/health/ready`. Connects with **`portfolio_admin`** DML role.

Inside both `server/server-public` and `server/server-admin`, organize packages strictly inward:
`api` (Ktor routes, `StatusPages`, request parsing) → `application` (`*QueryService` & `*CommandService`) → `infrastructure` (`server/server-db` adapters, R2 client, JWT verifier) → `domain` (pure models/rules, `//core` contracts). Keep concrete classes `final` with primary-constructor `private val` dependency injection.

### 12. Data Access (Exposed 1.0 DSL-Only) & Pagination

- **Exposed 1.0 DSL Only:** Use `org.jetbrains.exposed.v1.*` DSL queries mapped directly into immutable `data class` domain models inside `newSuspendedTransaction(Dispatchers.IO)`.
  - **Prohibited:** Exposed DAO entities (`Entity` / `IntEntity` lazy-loading active record objects) and `SchemaUtils.create()` in production.
  - Never leak Exposed `Table` definitions or `ResultRow` objects out of `server/server-db` / `infrastructure` into `application` or `api` layers.
- **Connection Pooling:** Always connect through HikariCP with explicit pool bounds, `isAutoCommit = false`, and parameterized connection settings from environment variables.
- **Pagination & CWE-190 Overflow Guards:**
  - All collection queries accept immutable `QueryOptions` and return `@Serializable PagedResult<T>`.
  - Clamp `pageSize` between `MIN_PAGE_SIZE` (1) and `MAX_PAGE_SIZE` (100), defaulting to `DEFAULT_PAGE_SIZE` (20).
  - Validate offset arithmetic `(page - 1L) * pageSize` against `Int.MAX_VALUE` (and DB `BIGINT` bounds) at query normalization time; reject out-of-range offsets with `ApiError.ValidationError` (`400 Bad Request`) ([CWE-190](https://cwe.mitre.org/data/definitions/190.html)).
- **Safe Sorting / Filtering:** Validate any dynamic sort/filter field against explicit column allowlists (never raw interpolated strings).

### 13. HTTP Semantics, Auth, Uploads & Health Probes

- **Standard Mutation Semantics:**
  - `POST` → `201 Created` + `Location` header + persisted resource
  - `PUT` → `200 OK` (or `404 Not Found` if target ID does not exist)
  - `DELETE` → `204 No Content` (or `404 Not Found`)
  - Postgres foreign-key (`23503`) or unique (`23505`) violations → `409 Conflict` (`ApiError.Conflict`)
  - Unmatched routes / missing resources → `404 Not Found` (never `500`)
- **Server-Owned Fields:** Generate UUIDs and audit timestamps (`created_at`, `updated_at`) server-side using an injected `java.time.Clock` — never trust client-supplied IDs or timestamps.
- **Deferred Optimistic Concurrency (Tracked Follow-Up):** `ETag` / `If-Match` concurrency checks on `PUT` updates are deferred for v1 (documented in [ADR 8](docs/architecture-decisions.md#adr-8-stateless-pagination-pure-get-downloads-and-deferred-concurrency-tokens)) and guarded only by database constraints until added.
- **Admin Authentication:** Protect every `/api/v1/admin/*` route unconditionally (no global disable switch). Support GitHub/Google OAuth allowlisted to owner ID + Cloudflare Access `Cf-Access-Jwt-Assertion` verification in production, and a dedicated dev-token profile (`scripts/dev-token.sh`) strictly disabled outside local/test profiles.
- **Cloudflare R2 Upload Security:**
  - Use direct-to-R2 presigned PUT URLs (`POST /documents/presign` → direct upload → `POST /documents/confirm`).
  - On confirm, verify object existence, byte size (`<= maxUploadBytes`), and MIME type via magic-byte header inspection (not filename extension). Generate UUID `storage_key`s and serve downloads with `X-Content-Type-Options: nosniff` and safe `Content-Disposition`.
- **Health Endpoints (Unauthenticated & Cheap):**
  - `GET /health` — liveness only (process up).
  - `GET /health/ready` — verifies Postgres connectivity and confirms the expected Flyway schema version is present before traffic is routed.
- **Web SEO & `/read/{slug}`:**
  - `server/server-public` serves `/read/{slug}` via [Ktor HTML DSL](https://ktor.io/docs/server-html-dsl.html) (clean semantic HTML article, canonical link, JSON-LD `Article`, link into interactive Wasm view for embedded demos), plus `/sitemap.xml`, `/robots.txt`, dynamic per-route HTML shell metadata for the `wasmJs` app, and Brotli/Gzip compression.

---

## Part IV: PostgreSQL 16 & Flyway Standards

### 14. Schema Isolation, Least-Privilege Roles & Lifecycle

1. **Explicit Schemas (Never `public`):** Organize all tables under `portfolio.*` (career, projects, writeups, documents) and `analytics.*` (raw cookieless events, daily rollups). Qualify schema names explicitly in queries and DDL.
2. **Three-Tier Database Roles:**
   - **`portfolio_migrator`**: Owns schema objects and executes DDL **only** inside the one-shot Flyway migrator container (with PostgreSQL advisory locking).
   - **`portfolio_admin`**: Read/write DML (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on `portfolio.*` and `SELECT` on `analytics.*` — zero DDL privileges.
   - **`portfolio_public`**: `SELECT` on published/public `portfolio.*` views/tables and `INSERT`-only on `analytics.events` — zero `UPDATE`, `DELETE`, or DDL privileges.
3. **Migration Hygiene:**
   - Versioned scripts follow `V<version>__<snake_case_description>.sql` and are **immutable** once merged.
   - Repeatable views/read-models/pure functions use `R__<snake_case_description>.sql`.
   - Seed data lives separately under `scripts/01_seed_dev_data.sql` using declarative `generate_series` sets (never procedural loops or production deployment coupling).

### 15. PostgreSQL DDL, Volatility & SQL Testing Rules

- **Modern Types Only:**
  - Use `UUID` or `BIGINT GENERATED BY DEFAULT AS IDENTITY` — **never** legacy `SERIAL`.
  - Use `TIMESTAMPTZ` (UTC) everywhere — **never** timezone-naive `TIMESTAMP`.
- **Mandatory Foreign Key Indexes:** Every `REFERENCES parent(id)` column must have an explicit B-Tree index to prevent sequential scans and parent lock contention.
- **Explicit Function Volatility:** Every SQL/PLpgSQL function must declare `IMMUTABLE`, `STABLE`, or `VOLATILE`. Reserve `CREATE PROCEDURE` for side-effecting batch maintenance (such as daily analytics rollup compaction).
- **Parameterized Tooling:** CLI scripts invoking `psql` must stream SQL via stdin and pass arguments via `psql -v key="value"` (`:'key'`), never shell string interpolation inside `-c` ([psql Variables](https://www.postgresql.org/docs/current/app-psql.html#APP-PSQL-VARIABLES)).
- **SQL Verification Suite:** Maintain `tests/01_schema_structure_tests.sql` asserting required schemas, tables, FK indexes, `CHECK` constraints, and three-tier role permissions.

---

## Part V: Docker, Testing, Bruno, Bash & Pre-Commit Standards

### 16. Container Images & Docker Compose

- **Multi-Stage, Non-Root `Dockerfile`s:**
  - Build artifacts in a JDK/SDK stage (`eclipse-temurin:<JAVA_VERSION>-jdk`), run on minimal JRE (`eclipse-temurin:<JAVA_VERSION>-jre`) as a dedicated non-root user.
  - Size JVM memory dynamically via `-XX:MaxRAMPercentage=75.0` (never hardcoded `-Xmx`), add OCI metadata labels (`org.opencontainers.image.*`), and maintain strict `.dockerignore` exclusions (`build/`, `.kotlin/`, `.env`, `.idea/`).
- **Environment-Only Configuration:** Document every variable in `.env.example`; keep real `.env` git-ignored and never bake credentials into images or `compose.yaml`.
- **Profiled `compose.yaml`:**
  - Group services into composable profiles (`deps`: Postgres 16 + one-shot `db-migrator` + MinIO; `local`: full `deps` + containerized `server/server-public` + `server/server-admin`; `test`: ephemeral verification runner).
  - Bind host ports to `${HOST_BIND_ADDRESS:-127.0.0.1}`.
  - Every long-running container defines a meaningful `healthcheck` (e.g., querying migrated schema readiness, not just port TCP check); dependent services gate startup with `depends_on: { condition: service_healthy }`.

### 17. Test Pyramid, Architecture Fitness & Bruno Suite

Follow [The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html):
1. **Fast Unit & Component Tests:** Pure `//core` serialization/pagination/`ApiError` tests, FlowMVI store tests, and Ktor `testHost` route contract tests running in milliseconds with zero DB.
2. **Architecture Fitness Tests ([Konsist](https://docs.konsist.lemonappdev.com/)):** Automated verification that `//app/presentation` never imports Compose, `//core` has no server/UI imports, `server/server-public` never touches admin packages, and domain `data class`es declare no `var` properties.
3. **Docker / Testcontainers Integration Tests:** Real PostgreSQL 16 + Flyway + MinIO container tests verifying Exposed 1.0 queries, FK/unique `409` mappings, role least-privilege enforcement, and R2 presign/confirm flows.
4. **Bruno API Collection (`bruno/`):**
   - Organize chronologically: `00-Health`, `01-Public-Portfolio`, `02-Public-Writeups-And-SEO`, `03-Admin-CMS`, `04-Admin-Documents-And-Stats`, `05-Negative-Tests` (400 overflow, 401/403 auth, 404 missing slug/route, 409 duplicate slug/FK conflict).
   - Use Bruno environment variables (`{{publicBaseUrl}}`, `{{adminBaseUrl}}`, `{{adminToken}}`) — zero hardcoded URLs or tokens.

### 18. Bash Scripting & Pre-Commit Governance

- **Bash Standards (`scripts/`):**
  - Begin executable scripts with `#!/usr/bin/env bash` and `set -euo pipefail`.
  - `source` shared helpers from `scripts/lib.sh` (`# shellcheck source=lib.sh`), validate inputs up front, check prerequisites via `require_command`, quote all expansions (`"${var}"`), use `local` inside functions, declare constants `readonly`, indent 4 spaces (`shfmt -i 4`), and keep scripts idempotent (`up.sh`, `down.sh`, `smoke.sh`, `dev-token.sh`).
- **Formatting & `.githooks/pre-commit`:**
  - `.editorconfig` enforces 4-space indentation, UTF-8, LF endings, trimmed trailing whitespace, and final newline across `.kt`, `.kts`, `.sql`, `.sh`, `.yml`, `.yaml`.
  - Pre-commit hook automatically formats staged Kotlin/Bash files, removes unused Kotlin imports, checks `bash -n`, re-stages modified files, and **blocks edits to already-committed `migrations/V*.sql` files**.

---

## Part VI: Anti-Pattern & Remedy Catalog

| Anti-Pattern | Risk | Required Remedy |
| :--- | :--- | :--- |
| Mutable collections/vars in models (`MutableList`, `var`) | Race conditions, broken FlowMVI equality, unpredictable UI state | Use `data class` + `val` + `List`/`Map`/`Set` only |
| Mutating state inside `GET` routes (e.g. download counters) | Breaks HTTP idempotency & forces write DB creds on `server/server-public` | Keep `GET` 100% read-only; log telemetry via `POST /events` |
| Sharing `public` schema or using single superuser DB role | Excessive blast radius; public compromise risks CMS/data integrity | Separate `portfolio`/`analytics` schemas + 3-tier DB roles |
| Running `SchemaUtils.create()` or editing merged `V*.sql` | Schema drift, checksum failures, unsafe production DDL | Dedicated one-shot Flyway migrator + append-only `V<n>__*.sql` |
| Using `SERIAL`, `TIMESTAMP`, or unindexed foreign keys | Non-standard sequences, lost UTC offsets, FK lock/scan bottlenecks | Use `GENERATED BY DEFAULT AS IDENTITY` / `UUID`, `TIMESTAMPTZ`, B-Tree FK indexes |
| Importing Compose in `//app/presentation` | Breaks native SwiftUI sharing on iOS | Keep `//app/presentation` pure KMP; enforce with Konsist |
| Switching Compose layout by OS/device model | Breaks window resizing, foldables, and split-screen tablets | Drive layouts via `WindowSizeClass` `dp` breakpoints + modality |
| Reflection JSON serializers or `encodeDefaults = false` | Strips `is*` boolean prefixes; inconsistent JSON payloads | Frozen `kotlinx.serialization` (`encodeDefaults = true`) |
| Exposing raw `500` exception details in production | Leaks DB strings, hostnames, and stack traces | Pure `Throwable.toApiError(isDev)` masking `500` details with `traceId` |
| Unbounded page sizes or unchecked `(page - 1) * pageSize` | Memory exhaustion / 32-bit integer overflow (CWE-190) | Clamp `pageSize <= 100`; check overflow before cast (`400`) |
| Storing uploaded PDFs/images in Postgres `BYTEA` | Table/WAL bloat, slow backups, Ktor heap spikes | Direct presigned PUTs to Cloudflare R2 (MinIO locally) |
| Deploying `:latest` tags or baking secrets into images/YAML | Non-deterministic rollouts and credential exposure | Immutable content-hash tags, `.env.example`, secret stores |
