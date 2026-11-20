# Portfolio — Architecture Decision Records (ADR)

This document records the core architectural decisions, trade-offs, guardrails, and authoritative references for the **Portfolio** platform. Every ADR maps directly to enforcement rules in [CODING_STANDARDS.md](../CODING_STANDARDS.md) and execution tasks in [PLAN.md](PLAN.md).

---

## Decision Matrix

| ADR | Area | Selected Approach | Status |
| :--- | :--- | :--- | :--- |
| [ADR 1](#adr-1-universal-coding-standards-functional-core-immutability-cqs-zero-magic-numbers) | Code Hygiene & Style | Functional-first, `val` immutability, CQS, zero magic numbers, extension helpers | Accepted |
| [ADR 2](#adr-2-adaptive-ui-strategy-material-3-adaptive-dp-breakpoints-modality-and-custom-theme) | Public UI (Android, Desktop, `wasmJs`) | Material 3 Adaptive `dp` breakpoints (`Compact` / `Medium`–`Expanded` / `Large`+) + custom editorial theme on `wasmJs` | Accepted |
| [ADR 3](#adr-3-native-swiftui-on-ios-with-embedded-compose-demo-islands) | iOS Client | Native SwiftUI (`TabView`, `NavigationSplitView`) + embedded `ComposeUIViewController` writeup demo islands | Accepted |
| [ADR 4](#adr-4-presentation-and-navigation-decompose-and-flowmvi-in-compose-free-apppresentation) | Shared Presentation & Routing | Pure KMP `app/presentation` (zero Compose imports) using Decompose + FlowMVI | Accepted |
| [ADR 5](#adr-5-compile-time-split-ktor-jvm-servers-4b-and-separate-admin-ui) | Backend & Admin Topology | 4B compile-time split (`server/server-db`, `server/server-public`, `server/server-admin`) on Ktor JVM + dedicated `app/adminShared` UI | Accepted |
| [ADR 6](#adr-6-postgresql-16-exposed-10-dsl-only-one-shot-flyway-migrator-and-three-tier-roles) | Persistence & Schema Lifecycle | PostgreSQL 16 (`portfolio`, `analytics`), Exposed 1.0 DSL-only + HikariCP, one-shot Flyway migrator, 3 DB roles | Accepted |
| [ADR 7](#adr-7-pure-kmp-sealed-apierror-contract) | Client-Server Error Contract | Pure KMP `@Serializable sealed interface ApiError` in `//core` | Accepted |
| [ADR 8](#adr-8-stateless-pagination-pure-get-downloads-and-deferred-concurrency-tokens) | API Semantics & Concurrency | Immutable `QueryOptions`/`PagedResult<T>` (CWE-190 guards), read-only `GET`, deferred `ETag`/`If-Match` follow-up | Accepted |
| [ADR 9](#adr-9-cloudflare-r2-presigned-uploads-cookieless-analytics-and-ktor-readslug-seo) | Storage, Telemetry & Web SEO | Cloudflare R2 presigned PUT (MinIO locally), cookieless events + rollups, Ktor `/read/{slug}` + dynamic Wasm SEO | Accepted |
| [ADR 10](#adr-10-local-docker-emulation-testcontainers-konsist-fitness-functions-and-bruno-api-testing) | Containers & Verification | Profiled `compose.yaml`, Testcontainers, Konsist boundary assertions, Bruno API suite | Accepted |

---

## ADR 1: Universal Coding Standards (Functional Core, Immutability, CQS, Zero Magic Numbers)

### Status
Accepted

### Context
The codebase spans shared Kotlin Multiplatform contracts, pure Kotlin presentation/MVI logic, Compose UI, SwiftUI, Ktor JVM services, PostgreSQL DDL/SQL, Docker configuration, and Bash tooling. Without uniform rules across every layer, mutable state, side effects inside read paths, and duplicated boilerplate erode testability and safety over time.

### Decision
Adopt five universal engineering pillars codified in [CODING_STANDARDS.md](../CODING_STANDARDS.md):
1. **Functional First:** Prefer declarative transformations (`map`, `filter`, `fold`, `associate`, set-based SQL `generate_series`/CTEs) over mutable-accumulator loops; require inline justification comments for any permitted I/O or retry loop.
2. **Immutability by Default:** `data class` with `val` only, read-only `List`/`Map`/`Set`, immutable FlowMVI state, immutable merged `migrations/V*.sql` files, and `readonly` Bash constants.
3. **Command-Query Separation (CQS):** Separate pure queries from side-effecting commands (`Functional Core, Imperative Shell`), keeping DB/network/HTTP I/O strictly at outer boundaries with `.use { ... }` disposal.
4. **Zero Magic Numbers & Literals:** Centralize constants (`HttpStatusCode.*`, `QueryOptions.*`, enums, `const val`, `readonly`).
5. **Reusable Extension Helpers:** Consolidate mapping, validation, pagination, and shell operations into pure extension functions and `scripts/lib.sh`.

### Rationale & References
- Martin Fowler — [CommandQuerySeparation](https://martinfowler.com/bliki/CommandQuerySeparation.html) and [Value Object](https://martinfowler.com/bliki/ValueObject.html).
- Gary Bernhardt — [Functional Core, Imperative Shell](https://www.destroyallsoftware.com/screencasts/catalog/functional-core-imperative-shell).
- Bertrand Meyer — [Design by Contract](https://www.eiffel.com/values/design-by-contract/).
- JetBrains — [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) and [Kotlin Collections Overview](https://kotlinlang.org/docs/collections-overview.html).
- Google — [Shell Style Guide](https://google.github.io/styleguide/shellguide.html) and [ShellCheck](https://www.shellcheck.net/).

---

## ADR 2: Adaptive UI Strategy (Material 3 Adaptive `dp` Breakpoints, Modality, and Custom Theme)

### Status
Accepted

### Context
Visitors view the portfolio across phones, tablets, foldables, resizable desktop windows, and wide web monitors. Hardcoding layouts per OS or physical screen inches breaks window resizing and split-screen modes. At the same time, default Material 3 components require visual customization to read like an intentional engineering portfolio rather than a generic mobile app, and Web targets need strong Skia rendering performance.

### Decision
- Use **Option A (Material 3 Adaptive)** driven strictly by window size in `dp` via `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)`:
  - `Compact` (`< 600dp`): single pane, bottom navigation bar.
  - `Medium` (`600–839dp`) & `Expanded` (`840–1199dp`): navigation rail, multi-column grids, two-pane `ChildPanels` at `>= 840dp`.
  - `Large` (`1200–1599dp`) & `Extra-Large` (`>= 1600dp`): persistent desktop/web navigation chrome, supporting panes (e.g. Table of Contents / filters), and capped reading measure (`~720–840dp`) for technical articles.
- Separate **input modality** (hover states, keyboard shortcuts, desktop scrollbars) from layout size.
- Apply a custom portfolio design system (typography, flat hairline surfaces, mono code blocks) over M3 Adaptive primitives.
- Keep **`wasmJs`** as the web compilation target rather than Compose on `js` (which renders onto the same Skia `<canvas>` without gaining DOM SEO while noticeably degrading frame smoothness).
- Render technical writeups via [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer) with [Highlights](https://github.com/SnipMeDev/Highlights) syntax highlighting and compile-time `DemoRegistry` blocks (`demo:<id>`).

### Trade-offs & References
- Staying on Compose `wasmJs` trades browser-native `Ctrl+F`/Reader Mode in the rich app in exchange for one shared UI across Android/Desktop/Web and live Compose demos; [ADR 9](#adr-9-cloudflare-r2-presigned-uploads-cookieless-analytics-and-ktor-readslug-seo) restores crawler SEO and Reader Mode via `/read/{slug}`.
- Material Design — [Breakpoints / Window Size Classes](https://m3.material.io/foundations/layout/applying-layout/window-size-classes).
- Android Developers — [Adaptive Layouts in Compose](https://developer.android.com/develop/ui/compose/layouts/adaptive).
- JetBrains — [Compose Adaptive Layouts](https://kotlinlang.org/docs/multiplatform/compose-adaptive-layouts.html), [Kotlin/Wasm Overview](https://kotlinlang.org/docs/wasm-overview.html), and [Get Started with Kotlin/Wasm and Compose Multiplatform](https://kotlinlang.org/docs/wasm-get-started.html).

---

## ADR 3: Native SwiftUI on iOS with Embedded Compose Demo Islands

### Status
Accepted

### Context
iOS users expect native Apple navigation gestures, typography, and platform feel (`TabView`, `NavigationSplitView`), yet interactive technical demos inside writeups would be costly to maintain twice across Compose and SwiftUI.

### Decision
- Build `app/iosApp` in **native SwiftUI** consuming shared MVI state and navigation directly from `app/presentation` (Decompose `@StateValue` / `StackView`).
- Render writeup prose and syntax-highlighted code blocks natively in SwiftUI.
- Whenever a writeup embeds an interactive `demo:<id>` widget, mount that single Compose widget inline via `ComposeUIViewController` wrapped in `UIViewControllerRepresentable`.

### Trade-offs & References
- Requires maintaining SwiftUI views alongside `app/composeUi`, while eliminating duplicate maintenance of interactive writeup demos (at the minor cost of linking the Skiko runtime into the iOS binary for those embedded islands).
- JetBrains — [Integration with the SwiftUI Framework](https://kotlinlang.org/docs/multiplatform/swiftui-integration.html).

---

## ADR 4: Presentation and Navigation (Decompose and FlowMVI in Compose-Free `app/presentation`)

### Status
Accepted

### Context
Because iOS uses SwiftUI while Android, Desktop, and `wasmJs` use Compose Multiplatform, the shared presentation, state management, and navigation stack cannot depend on Compose UI libraries (ruling out Compose-coupled solutions). We also require strict MVI unidirectional data flow, multi-pane adaptive routing, and browser URL/history synchronization on `wasmJs`.

### Decision
- Isolate all navigation, state, repositories, and Ktor Client calls in a **pure KMP `app/presentation` module** with **zero Compose imports** (verified automatically via Konsist).
- Use **[Decompose](https://arkivanov.github.io/Decompose/component/overview/)** (`ComponentContext`, `ChildStack`, `ChildPanels`, and `WebHistoryController` on `wasmJs`) for UI-agnostic lifecycle-aware navigation and multi-pane state.
- Use **[FlowMVI](https://respawn.pro/FlowMVI/)** with its Essenty integration for strict immutable `State`/`Intent`/`Action` stores and reusable cross-cutting plugins (logging, error mapping, analytics hooks).

### Trade-offs & References
- Slightly more initial component wiring than Compose-only routers, rewarded by first-class SwiftUI sharing, built-in `wasmJs` URL synchronization, and clean parent-driven MVI navigation.
- Arkadii Ivanov — [Decompose](https://arkivanov.github.io/Decompose/).
- Respawn — [FlowMVI](https://respawn.pro/FlowMVI/).

---

## ADR 5: Compile-Time Split Ktor JVM Servers (4B) and Separate Admin UI

### Status
Accepted

### Context
Both the public portfolio and the admin CMS/analytics suite use Ktor and PostgreSQL. Choosing a runtime (JVM vs. Kotlin/Native) and deployment boundary determines library compatibility, blast radius, public web bundle size, and operational security.

### Decision
- **Stay on Ktor JVM (Netty):** Enables first-class JetBrains Exposed 1.0, HikariCP, Flyway, `ktor-server-auth` OAuth/JWT, and S3/R2 SDKs—none of which have full parity on Kotlin/Native CIO.
- **Adopt Topology 4B (Compile-Time Split in This Monorepo):**
  - `server/server-db` (`jvm/lib`): shared Exposed 1.0 DSL schema, repositories, HikariCP, and `BlobStorage`.
  - `server/server-public` (`jvm/app`): public read APIs, `/read/{slug}`, SEO shell, analytics ingestion. Does **not** compile or package admin routes, OAuth handlers, or R2 write logic.
  - `server/server-admin` (`jvm/app`): protected CMS CRUD, R2 presigned uploads, and admin analytics, deployable privately behind [Cloudflare Access](https://developers.cloudflare.com/cloudflare-one/policies/access/) and [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/).
- **Adopt Separate Admin UI (`app/adminShared`, `app/adminDesktopApp`, `app/adminWebApp`):** Dedicated Compose Desktop + `wasmJs` Web modules sharing `//core`, Decompose/FlowMVI, and `//app/composeUi` Markdown/Demo preview—adding **0 bytes** to public `webApp` and requiring **0 SwiftUI admin screens**.

### Trade-offs & References
- Keeping `server/server-public`, `server/server-admin`, and `server/server-db` in one monorepo captures the security and bundle isolation of separate deployable artifacts while avoiding cross-repo schema drift ([Shared Database](https://microservices.io/patterns/data/shared-database.html), [Monolith First](https://martinfowler.com/bliki/MonolithFirst.html)).
- Ktor — [Authentication](https://ktor.io/docs/authentication.html), [OAuth](https://ktor.io/docs/oauth.html), [JWT](https://ktor.io/docs/jwt.html), [Type-Safe Routing](https://ktor.io/docs/type-safe-routing.html), and [Server HTML DSL](https://ktor.io/docs/server-html-dsl.html).
- Cloudflare — [Cloudflare One](https://developers.cloudflare.com/cloudflare-one) · [Tailscale](https://tailscale.com).

---

## ADR 6: PostgreSQL 16, Exposed 1.0 DSL-Only, One-Shot Flyway Migrator, and Three-Tier Roles

### Status
Accepted

### Context
Kotlin Toolchain `0.13.0` supports Ktor, `kotlinx.serialization`, and KSP natively, whereas external SQL codegen plugins add friction. Furthermore, the backend must uphold strict application-database encapsulation, immutable migration history, and least-privilege access across public reads, admin writes, and schema DDL.

### Decision
- **Persistence Library:** Use **JetBrains Exposed 1.0 DSL-only** (`org.jetbrains.exposed.v1.*`) + **HikariCP** inside `newSuspendedTransaction(Dispatchers.IO)`, mapping directly to immutable `data class` models. Exposed DAO active-record entities and `SchemaUtils.create()` in production are prohibited.
- **Migrations:** Govern DDL exclusively through immutable versioned [Flyway](https://flywaydb.org/) scripts (`V<n>__*.sql`) and repeatable views (`R__*.sql`), executed by a **dedicated one-shot migrator** with PostgreSQL advisory locking (`pg_advisory_lock`).
- **Schema Isolation & Three-Tier Roles:**
  - Isolate tables into `portfolio.*` and `analytics.*` schemas (never `public`).
  - `portfolio_migrator`: DDL owner used solely by the one-shot migrator.
  - `portfolio_admin`: DML (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on `portfolio.*`, `SELECT` on `analytics.*` (used by `server/server-admin`).
  - `portfolio_public`: `SELECT` on `portfolio.*`, `INSERT`-only on `analytics.events` (used by `server/server-public`).
- **DDL Standards:** Require `UUID` or `BIGINT GENERATED BY DEFAULT AS IDENTITY` (no `SERIAL`), `TIMESTAMPTZ` everywhere, B-Tree indexes on every FK, and explicit `IMMUTABLE`/`STABLE`/`VOLATILE` declarations on functions.

### Trade-offs & References
- Exposed 1.0 DSL-only avoids ORM change-tracking/lazy-loading overhead while keeping SQL type-checked in pure Kotlin and compatible with Kotlin Toolchain `0.13.0`.
- JetBrains — [Exposed](https://www.jetbrains.com/exposed/) and [Exposed Documentation](https://jetbrains.com/exposed/documentation/).
- Martin Fowler — [Application Database](https://martinfowler.com/bliki/ApplicationDatabase.html), [Evolutionary Database Design](https://martinfowler.com/articles/evodb.html), and [Data Mapper](https://martinfowler.com/eaaCatalog/dataMapper.html).
- PostgreSQL — [Explicit Locking & Advisory Locks](https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS).

---

## ADR 7: Pure KMP Sealed `ApiError` Contract

### Status
Accepted

### Context
REST APIs commonly standardize error payloads using [IETF RFC 9457 (Problem Details)](https://datatracker.ietf.org/doc/html/rfc9457), [Google AIP-193](https://google.aip.dev/193), [Microsoft REST API Guidelines](https://github.com/microsoft/api-guidelines), [JSON:API Error Objects](https://jsonapi.org/format/#errors), or [Stripe-style typed errors](https://docs.stripe.com/api/errors). Here, we own both Ktor servers and all client apps via a shared `//core` Kotlin Multiplatform module compiled with `kotlinx.serialization`.

### Decision
Adopt **Option 2: a pure KMP `@Serializable sealed interface ApiError`** defined in `//core` with explicit `@SerialName` discriminators (`ValidationError`, `Unauthorized`, `Forbidden`, `NotFound`, `Conflict`, `InternalError`) and frozen `Json` settings (`encodeDefaults = true`, `ignoreUnknownKeys = true`, `explicitNulls = true`).
- Server `StatusPages` maps domain/validation/SQL errors (`23503`/`23505` → `409 Conflict`, unmatched routes → `404 Not Found`, overflow → `400 Bad Request`, unexpected errors → sanitized `500 InternalError` outside `dev`).
- FlowMVI stores switch exhaustively over `ApiError` via `when (error)` with compile-time completeness guarantees.

### Trade-offs & References
- **Trade-off:** Sacrifices generic `application/problem+json` Problem Details convention in exchange for zero-boilerplate, compile-time exhaustive Kotlin error unions shared identically across JVM, Android, iOS, and `wasmJs`. (Could be wrapped or mapped cleanly at an external API gateway later if third-party consumers are added.)
- JetBrains — [Kotlin Serialization](https://kotlinlang.org/docs/serialization.html) and [Kotlin Sealed Classes](https://kotlinlang.org/docs/sealed-classes.html).
- PostgreSQL — [Class 23 Integrity Constraint Violation Codes](https://www.postgresql.org/docs/current/errcodes-appendix.html).

---

## ADR 8: Stateless Pagination, Pure `GET` Downloads, and Deferred Concurrency Tokens

### Status
Accepted

### Context
Collection endpoints, document downloads, and admin mutations need predictable semantics that align with CQS, guard against integer overflow, and balance scope for v1.

### Decision
1. **Stateless Pagination (`QueryOptions` / `PagedResult<T>`):** Standardize `page` (`DEFAULT_PAGE = 1`), `pageSize` (`MIN = 1`, `DEFAULT = 20`, `MAX = 100`), `sortBy`, `sortDirection`, and explicit column-allowlisted filters. Validate `(page - 1L) * pageSize` against 32-bit `Int` bounds before SQL execution, returning `ApiError.ValidationError` (`400`) on overflow ([CWE-190](https://cwe.mitre.org/data/definitions/190.html)).
2. **Pure `GET` Downloads (Strict CQS):** `GET /api/v1/public/documents/{id}/download` performs zero database writes. Download telemetry is recorded exclusively via `POST /api/v1/public/events` (`DOCUMENT_DOWNLOAD`), preserving `GET` idempotency and keeping `portfolio_public` free of `UPDATE` or `SECURITY DEFINER` privileges.
3. **Standard HTTP Mutation Codes & Server-Owned Metadata:** `POST` returns `201` + `Location`, `PUT` returns `200` (or `404`), `DELETE` returns `204` (or `404`); UUIDs and `Clock`-derived audit timestamps are generated strictly server-side.
4. **Deferred Optimistic Concurrency (`ETag` / `If-Match`) — Tracked Follow-Up:** Optimistic locking tokens (`xmin` / version column with `ETag` + `If-Match`) are explicitly **deferred** for v1 (single-author admin CMS). Uniqueness and FK constraints still guard against invalid writes (`409 Conflict`), and `ETag`/`If-Match` remains a tracked follow-up if concurrent multi-tab editing requires conflict detection later.

### References
- IETF RFC 9110 — [HTTP Methods](https://datatracker.ietf.org/doc/html/rfc9110#section-9), [Conditional Requests (`ETag`/`If-Match`)](https://datatracker.ietf.org/doc/html/rfc9110#section-13.1.1), and [409 Conflict](https://datatracker.ietf.org/doc/html/rfc9110#name-409-conflict).
- Martin Fowler — [Optimistic Offline Lock](https://martinfowler.com/eaaCatalog/optimisticOfflineLock.html).
- Microsoft — [REST API Guidelines: Collections](https://github.com/microsoft/api-guidelines#9-collections).
- Markus Winand — [Pagination Performance](https://use-the-index-luke.com/sql/partial-results/fetch-next-page).

---

## ADR 9: Cloudflare R2 Presigned Uploads, Cookieless Analytics, and Ktor `/read/{slug}` SEO

### Status
Accepted

### Context
Binary resumes, slide decks, PDFs, and images bloat Postgres `BYTEA` storage and stress Ktor server memory if proxied indiscriminately. Meanwhile, portfolio traffic telemetry should be privacy-friendly without cookie banners, and `wasmJs` canvas rendering needs crawler-friendly SEO and fast readable articles.

### Decision
- **Object Storage ([Cloudflare R2](https://developers.cloudflare.com/r2/)):** Abstract storage behind `BlobStorage` in `server/server-db` (S3-compatible AWS SDK for Kotlin targeting Cloudflare R2 in production and MinIO locally). Use a two-step presign/confirm upload workflow (`POST /documents/presign` → direct PUT to R2 → `POST /documents/confirm` verifying size, object existence, and magic-byte MIME header).
- **Cookieless First-Party Analytics:** Record events (`PAGE_VIEW`, `WRITEUP_READ`, `PROJECT_CLICK`, `DOCUMENT_DOWNLOAD`) in `analytics.events` with daily-salted SHA-256 `visitor_hash` (never storing raw IPs or setting tracking cookies), plus `analytics.daily_rollups` for fast admin charts.
- **SEO & `/read/{slug}`:** Serve dynamic per-route `<title>`, `<meta>`, `og:*`, JSON-LD `ProfilePage`/`Article`, and `<noscript>` summaries on the `wasmJs` HTML shell, Brotli/Gzip compression, `/sitemap.xml`, `/robots.txt`, and a dedicated semantic HTML Reader View at `GET /read/{slug}` using [Ktor HTML DSL](https://ktor.io/docs/server-html-dsl.html).

### References
- Cloudflare — [R2 Documentation](https://developers.cloudflare.com/r2/).
- Ktor — [Multipart & Requests](https://ktor.io/docs/requests.html) and [Server HTML DSL](https://ktor.io/docs/server-html-dsl.html).
- OWASP — [API Security Top 10](https://owasp.org/www-project-api-security/).

---

## ADR 10: Local Docker Emulation, Testcontainers, Konsist Fitness Functions, and Bruno API Testing

### Status
Accepted

### Context
With Postgres 16 roles, Flyway migrations, MinIO/R2 object storage, and two distinct Ktor servers, unit tests alone cannot catch SQL dialect issues, permission gaps, role leaks, or module boundary drift.

### Decision
- **Profiled Local Docker Stack (`compose.yaml`):** Provide `deps` (Postgres 16 + one-shot Flyway `db-migrator` + MinIO), `local` (plus containerized `server/server-public` and `server/server-admin`), and `test` profiles gated on meaningful schema-readiness `healthcheck`s and bound to `127.0.0.1`.
- **Multi-Stage Non-Root Dockerfiles:** Build on JDK, run on minimal JRE as non-root with `-XX:MaxRAMPercentage`, OCI labels, and `.env.example` externalized configuration.
- **Layered Verification:** Combine unit tests (`//core`, FlowMVI), Ktor `testHost` contract tests, **Konsist** architectural fitness tests (blocking Compose imports in `app/presentation` and admin leaks in `server/server-public`), **Docker/Testcontainers** Postgres + MinIO integration tests, SQL schema structure assertions, and a full **Bruno** API test suite (`bruno/` `00-Health` → `05-Negative-Tests`) automated via `scripts/smoke.sh --bruno`.

### References
- Martin Fowler — [The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html).
- Docker — [Using Profiles with Compose](https://docs.docker.com/compose/how-tos/profiles/) and [Multi-Stage Building Best Practices](https://docs.docker.com/build/building/best-practices/).
- OWASP — [Docker Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html) and [Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html).
- Konsist — [Documentation](https://lemonappdev.github.io/konsist/).
- The Twelve-Factor App — [12factor.net](https://12factor.net/).
